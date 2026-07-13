package com.example.aigateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.entity.RequestLog;
import com.example.aigateway.entity.UsageRecord;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.messaging.GatewayEventPublisher;
import com.example.aigateway.provider.ProviderAdapter;
import com.example.aigateway.provider.ProviderAdapterFactory;
import com.example.aigateway.provider.ProviderCredential;
import com.example.aigateway.security.ApiKeyPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** Verifies model call service provider key failover behavior. */
class ModelCallServiceProviderKeyFailoverTest {

    @Test
    void chatShouldFailoverToNextProviderKeyAndBillWinningKey() {
        ProviderAdapterFactory adapterFactory = mock(ProviderAdapterFactory.class);
        ProviderKeySelectorService selectorService = mock(ProviderKeySelectorService.class);
        ProviderKeyAvailabilityService availabilityService = mock(ProviderKeyAvailabilityService.class);
        ModelService modelService = mock(ModelService.class);
        BillingService billingService = mock(BillingService.class);
        GatewayEventPublisher eventPublisher = mock(GatewayEventPublisher.class);
        ProviderAdapter adapter = mock(ProviderAdapter.class);
        ProviderModelPricing model = model();
        ChatRequest request = request();
        ProviderCredential first = credential(1L, "sk-first");
        ProviderCredential second = credential(2L, "sk-second");
        BusinessException rateLimited = new BusinessException(
                "PROVIDER_RATE_LIMITED",
                "Provider rate limit exceeded",
                HttpStatus.TOO_MANY_REQUESTS);
        ChatResponse upstreamResponse = response();

        when(modelService.getAvailableModelByCode("OPENAI", "gpt-test")).thenReturn(model);
        when(selectorService.selectCredentials(model, 100L)).thenReturn(List.of(first, second));
        when(adapterFactory.getAdapter("OPENAI")).thenReturn(adapter);
        when(adapter.chat(request, first)).thenThrow(rateLimited);
        when(adapter.chat(request, second)).thenReturn(upstreamResponse);
        when(billingService.recordSuccessfulUsage(
                any(RequestLog.class),
                any(UsageRecord.class),
                isNull(),
                anyString(),
                anyString())).thenReturn(true);

        ModelCallService service = new ModelCallService(
                adapterFactory,
                selectorService,
                availabilityService,
                modelService,
                null,
                new UpstreamErrorService(),
                billingService,
                null,
                new ObjectMapper(),
                eventPublisher);

        ChatResponse result = service.chat(request, new ApiKeyPrincipal(10L, 100L, "ak_test"), null);

        assertEquals("gpt-test", result.getModel());
        verify(availabilityService).markFailure(1L, rateLimited, rateLimited);
        verify(availabilityService).markSuccess(2L);

        ArgumentCaptor<RequestLog> requestLogCaptor = ArgumentCaptor.forClass(RequestLog.class);
        ArgumentCaptor<UsageRecord> usageRecordCaptor = ArgumentCaptor.forClass(UsageRecord.class);
        verify(billingService).recordSuccessfulUsage(
                requestLogCaptor.capture(),
                usageRecordCaptor.capture(),
                isNull(),
                anyString(),
                anyString());
        assertEquals(2L, requestLogCaptor.getValue().getProviderKeyId());
        assertEquals(2L, usageRecordCaptor.getValue().getProviderKeyId());
    }

    private ProviderCredential credential(Long providerKeyId, String apiKey) {
        return new ProviderCredential(providerKeyId, "OFFICIAL_API_KEY", 1L, "OPENAI", apiKey, "https://example.test/v1");
    }

    private ProviderModelPricing model() {
        ProviderModelPricing model = new ProviderModelPricing();
        model.setProviderId(1L);
        model.setProviderCode("OPENAI");
        model.setModelId(20L);
        model.setModelCode("gpt-test");
        model.setInputTokenPrice(new BigDecimal("0.01"));
        model.setOutputTokenPrice(new BigDecimal("0.02"));
        return model;
    }

    private ChatRequest request() {
        ChatRequest request = new ChatRequest();
        request.setProviderCode("OPENAI");
        request.setModel("gpt-test");
        ChatRequest.Message message = new ChatRequest.Message();
        message.setRole("user");
        message.setContent("hi");
        request.setMessages(List.of(message));
        return request;
    }

    private ChatResponse response() {
        ChatResponse response = new ChatResponse();
        response.setModel("gpt-test");
        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(2);
        usage.setCompletionTokens(3);
        usage.setTotalTokens(5);
        response.setUsage(usage);
        return response;
    }
}
