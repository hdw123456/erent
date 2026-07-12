package com.example.aigateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.entity.RequestLog;
import com.example.aigateway.entity.IdempotencyRecord;
import com.example.aigateway.entity.UsageRecord;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.gateway.stream.GatewayStreamProtocol;
import com.example.aigateway.gateway.stream.GatewayStreamSink;
import com.example.aigateway.gateway.stream.GatewayStreamResponseAdapter;
import com.example.aigateway.mapper.IdempotencyRecordMapper;
import com.example.aigateway.provider.ProviderAdapter;
import com.example.aigateway.provider.ProviderAdapterFactory;
import com.example.aigateway.provider.ProviderCredential;
import com.example.aigateway.provider.ProviderStreamEvent;
import com.example.aigateway.security.ApiKeyPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;

/** Verifies model call service streaming behavior. */
class ModelCallServiceStreamingTest {

    @Test
    void streamFailsOverBeforeFirstEventAndBillsWinningKey() {
        Fixture fixture = new Fixture();
        ProviderCredential first = credential(1L);
        ProviderCredential second = credential(2L);
        BusinessException rateLimited = new BusinessException(
                "PROVIDER_RATE_LIMITED", "rate limited", HttpStatus.TOO_MANY_REQUESTS);
        when(fixture.selector.selectCredentials(fixture.model, 100L)).thenReturn(List.of(first, second));
        when(fixture.adapter.stream(fixture.request, first)).thenReturn(Flux.error(rateLimited));
        when(fixture.adapter.stream(fixture.request, second)).thenReturn(Flux.just(
                event("Hello", null, false),
                event(null, usage(), false),
                ProviderStreamEvent.done("[DONE]")));

        fixture.service.stream(
                fixture.request,
                fixture.principal,
                null,
                "chat_completions",
                "{\"model\":\"gpt-test\",\"stream\":true}",
                GatewayStreamProtocol.OPENAI_CHAT_COMPLETIONS);

        verify(fixture.availability, timeout(2000)).markFailure(1L, rateLimited, rateLimited);
        verify(fixture.availability, timeout(2000)).markSuccess(2L);
        ArgumentCaptor<RequestLog> log = ArgumentCaptor.forClass(RequestLog.class);
        ArgumentCaptor<UsageRecord> usage = ArgumentCaptor.forClass(UsageRecord.class);
        verify(fixture.billing, timeout(2000)).recordSuccessfulUsage(
                log.capture(), usage.capture(), isNull(), anyString(), anyString());
        assertEquals(2L, log.getValue().getProviderKeyId());
        assertEquals(2L, usage.getValue().getProviderKeyId());
        assertEquals("PROVIDER", usage.getValue().getUsageSource());
        assertEquals(7, usage.getValue().getTotalTokens());
    }

    @Test
    void streamFailsOverWhenAdapterThrowsBeforeReturningFlux() {
        Fixture fixture = new Fixture();
        ProviderCredential first = credential(1L);
        ProviderCredential second = credential(2L);
        BusinessException rateLimited = new BusinessException(
                "PROVIDER_RATE_LIMITED", "rate limited", HttpStatus.TOO_MANY_REQUESTS);
        when(fixture.selector.selectCredentials(fixture.model, 100L)).thenReturn(List.of(first, second));
        when(fixture.adapter.stream(fixture.request, first)).thenThrow(rateLimited);
        when(fixture.adapter.stream(fixture.request, second)).thenReturn(Flux.just(
                event("Hello", null, false),
                event(null, usage(), false),
                ProviderStreamEvent.done("[DONE]")));

        fixture.service.stream(
                fixture.request,
                fixture.principal,
                null,
                "chat_completions",
                "{\"model\":\"gpt-test\",\"stream\":true}",
                GatewayStreamProtocol.OPENAI_CHAT_COMPLETIONS);

        verify(fixture.availability, timeout(2000)).markFailure(1L, rateLimited, rateLimited);
        verify(fixture.availability, timeout(2000)).markSuccess(2L);
        verify(fixture.billing, timeout(2000)).recordSuccessfulUsage(
                any(RequestLog.class), any(UsageRecord.class), isNull(), anyString(), anyString());
    }

    @Test
    void streamBillsPartialUsageWhenProviderFailsAfterOutput() {
        Fixture fixture = new Fixture();
        ProviderCredential credential = credential(1L);
        BusinessException failure = new BusinessException(
                "PROVIDER_UPSTREAM_ERROR", "failed", HttpStatus.BAD_GATEWAY);
        when(fixture.selector.selectCredentials(fixture.model, 100L)).thenReturn(List.of(credential));
        when(fixture.adapter.stream(fixture.request, credential)).thenReturn(
                Flux.concat(Flux.just(event("Partial", null, false)), Flux.error(failure)));

        fixture.service.stream(
                fixture.request,
                fixture.principal,
                null,
                "chat_completions",
                "{\"model\":\"gpt-test\",\"stream\":true}",
                GatewayStreamProtocol.OPENAI_CHAT_COMPLETIONS);

        ArgumentCaptor<UsageRecord> usage = ArgumentCaptor.forClass(UsageRecord.class);
        verify(fixture.billing, timeout(2000)).recordPartialUsage(
                org.mockito.ArgumentMatchers.any(RequestLog.class),
                usage.capture(),
                isNull(),
                anyString(),
                org.mockito.ArgumentMatchers.eq("PROVIDER_UPSTREAM_ERROR"));
        assertEquals("ESTIMATED", usage.getValue().getUsageSource());
    }

    @Test
    void clientCancellationStopsProviderAndBillsEmittedPartialUsage() throws Exception {
        Fixture fixture = new Fixture();
        ProviderCredential credential = credential(1L);
        AtomicBoolean providerCancelled = new AtomicBoolean(false);
        GatewayStreamSink sink = mock(GatewayStreamSink.class);
        when(fixture.selector.selectCredentials(fixture.model, 100L)).thenReturn(List.of(credential));
        when(fixture.adapter.stream(fixture.request, credential)).thenReturn(
                Flux.concat(Flux.just(event("Partial", null, false)), Flux.never())
                        .doOnCancel(() -> providerCancelled.set(true)));

        Runnable cancel = fixture.service.streamToSink(
                fixture.request,
                fixture.principal,
                null,
                "chat_completions",
                "{\"model\":\"gpt-test\",\"stream\":true}",
                GatewayStreamProtocol.OPENAI_CHAT_COMPLETIONS,
                sink);
        verify(sink, timeout(2000)).send(any());

        cancel.run();

        verify(fixture.billing, timeout(2000)).recordPartialUsage(
                any(RequestLog.class),
                any(UsageRecord.class),
                isNull(),
                anyString(),
                org.mockito.ArgumentMatchers.eq("CLIENT_STREAM_CLOSED"));
        assertTrue(providerCancelled.get());
    }

    @Test
    void completedIdempotentStreamReplaysWithoutCallingProviderAgain() {
        Fixture fixture = new Fixture();
        ProviderCredential credential = credential(1L);
        when(fixture.selector.selectCredentials(fixture.model, 100L)).thenReturn(List.of(credential));
        when(fixture.adapter.stream(fixture.request, credential)).thenReturn(Flux.just(
                event("Hello", null, false),
                event(null, usage(), false),
                ProviderStreamEvent.done("[DONE]")));
        when(fixture.idempotencyMapper.insertIdempotencyRecordIgnore(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> {
                    IdempotencyRecord record = invocation.getArgument(0);
                    record.setId(77L);
                    return 1;
                });

        String payload = "{\"model\":\"gpt-test\",\"stream\":true}";
        fixture.service.stream(
                fixture.request,
                fixture.principal,
                "idem-1",
                "chat_completions",
                payload,
                GatewayStreamProtocol.OPENAI_CHAT_COMPLETIONS);

        ArgumentCaptor<IdempotencyRecord> claim = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(fixture.idempotencyMapper).insertIdempotencyRecordIgnore(claim.capture());
        ArgumentCaptor<String> replayJson = ArgumentCaptor.forClass(String.class);
        verify(fixture.billing, timeout(2000)).recordSuccessfulUsage(
                any(RequestLog.class),
                any(UsageRecord.class),
                org.mockito.ArgumentMatchers.eq(77L),
                anyString(),
                replayJson.capture());

        IdempotencyRecord completed = claim.getValue();
        completed.setStatus("COMPLETED");
        completed.setResponseJson(replayJson.getValue());
        when(fixture.idempotencyMapper.insertIdempotencyRecordIgnore(any(IdempotencyRecord.class))).thenReturn(0);
        when(fixture.idempotencyMapper.getByScopeAndIdempotencyKeyHash(
                completed.getScope(), completed.getIdempotencyKeyHash())).thenReturn(completed);
        clearInvocations(fixture.adapter);

        fixture.service.stream(
                fixture.request,
                fixture.principal,
                "idem-1",
                "chat_completions",
                payload,
                GatewayStreamProtocol.OPENAI_CHAT_COMPLETIONS);

        verifyNoInteractions(fixture.adapter);
        verify(fixture.billing, times(1)).recordSuccessfulUsage(
                any(RequestLog.class),
                any(UsageRecord.class),
                org.mockito.ArgumentMatchers.eq(77L),
                anyString(),
                anyString());
    }

    private ProviderCredential credential(long id) {
        return new ProviderCredential(id, "OFFICIAL_API_KEY", 1L, "OPENAI", "sk-test", "https://example.test/v1");
    }

    private ProviderStreamEvent event(String text, ChatResponse.Usage usage, boolean done) {
        String raw = done ? "[DONE]" : "{\"choices\":[]}";
        return new ProviderStreamEvent(raw, "chat_1", "gpt-test", "assistant", text, null, usage, done);
    }

    private ChatResponse.Usage usage() {
        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(4);
        usage.setCompletionTokens(3);
        usage.setTotalTokens(7);
        return usage;
    }

    private static final class Fixture {
        private final ProviderAdapterFactory adapterFactory = mock(ProviderAdapterFactory.class);
        private final ProviderKeySelectorService selector = mock(ProviderKeySelectorService.class);
        private final ProviderKeyAvailabilityService availability = mock(ProviderKeyAvailabilityService.class);
        private final ModelService models = mock(ModelService.class);
        private final BillingService billing = mock(BillingService.class);
        private final IdempotencyRecordMapper idempotencyMapper = mock(IdempotencyRecordMapper.class);
        private final ProviderAdapter adapter = mock(ProviderAdapter.class);
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final ProviderModelPricing model = model();
        private final ChatRequest request = request();
        private final ApiKeyPrincipal principal = new ApiKeyPrincipal(10L, 100L, "ak_test");
        private final ModelCallService service;

        private Fixture() {
            when(models.getAvailableModelByCode("OPENAI", "gpt-test")).thenReturn(model);
            when(adapterFactory.getAdapter("OPENAI")).thenReturn(adapter);
            service = new ModelCallService(
                    adapterFactory,
                    selector,
                    availability,
                    models,
                    new GatewayStreamResponseAdapter(objectMapper),
                    new UpstreamErrorService(),
                    billing,
                    idempotencyMapper,
                    objectMapper);
        }

        private static ProviderModelPricing model() {
            ProviderModelPricing model = new ProviderModelPricing();
            model.setProviderId(1L);
            model.setProviderCode("OPENAI");
            model.setModelId(20L);
            model.setModelCode("gpt-test");
            model.setInputTokenPrice(new BigDecimal("0.01"));
            model.setOutputTokenPrice(new BigDecimal("0.02"));
            return model;
        }

        private static ChatRequest request() {
            ChatRequest request = new ChatRequest();
            request.setProviderCode("OPENAI");
            request.setModel("gpt-test");
            ChatRequest.Message message = new ChatRequest.Message();
            message.setRole("user");
            message.setContent("hello");
            request.setMessages(List.of(message));
            return request;
        }
    }
}
