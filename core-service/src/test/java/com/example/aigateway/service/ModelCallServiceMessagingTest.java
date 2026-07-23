package com.example.aigateway.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.entity.RequestLog;
import com.example.aigateway.entity.UsageRecord;
import com.example.aigateway.messaging.GatewayEventPublisher;
import com.example.aigateway.messaging.event.RequestCompletedEvent;
import com.example.aigateway.messaging.event.UsageRecordedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;

/** Verifies best-effort event publishing after the core billing transaction. */
class ModelCallServiceMessagingTest {

    @Test
    void newlyRecordedUsageShouldPublishBothEvents() {
        BillingService billingService = mock(BillingService.class);
        GatewayEventPublisher eventPublisher = mock(GatewayEventPublisher.class);
        ModelCallService service = service(billingService, eventPublisher);
        when(billingService.recordSuccessfulUsage(
                any(RequestLog.class),
                any(UsageRecord.class),
                isNull(),
                anyString(),
                anyString())).thenReturn(true);

        service.writeUsageRecord(requestLog(), usage(), model(), null, "fingerprint", "{}");

        ArgumentCaptor<RequestCompletedEvent> requestEvent = ArgumentCaptor.forClass(RequestCompletedEvent.class);
        ArgumentCaptor<UsageRecordedEvent> usageEvent = ArgumentCaptor.forClass(UsageRecordedEvent.class);
        verify(eventPublisher).publishRequestCompleted(requestEvent.capture());
        verify(eventPublisher).publishUsageRecorded(usageEvent.capture());
        assertEquals("req_1", requestEvent.getValue().requestId());
        assertEquals("req_1", usageEvent.getValue().requestId());
        assertEquals(30, usageEvent.getValue().totalTokens());
    }

    @Test
    void duplicateUsageShouldNotPublishEvents() {
        BillingService billingService = mock(BillingService.class);
        GatewayEventPublisher eventPublisher = mock(GatewayEventPublisher.class);
        ModelCallService service = service(billingService, eventPublisher);
        when(billingService.recordSuccessfulUsage(
                any(RequestLog.class),
                any(UsageRecord.class),
                isNull(),
                anyString(),
                anyString())).thenReturn(false);

        service.writeUsageRecord(requestLog(), usage(), model(), null, "fingerprint", "{}");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rabbitFailureShouldNotEscapeAfterCoreTransactionCommits() {
        BillingService billingService = mock(BillingService.class);
        GatewayEventPublisher eventPublisher = mock(GatewayEventPublisher.class);
        ModelCallService service = service(billingService, eventPublisher);
        when(billingService.recordSuccessfulUsage(
                any(RequestLog.class),
                any(UsageRecord.class),
                isNull(),
                anyString(),
                anyString())).thenReturn(true);
        doThrow(new AmqpException("RabbitMQ unavailable"))
                .when(eventPublisher)
                .publishRequestCompleted(any(RequestCompletedEvent.class));

        assertDoesNotThrow(
                () -> service.writeUsageRecord(requestLog(), usage(), model(), null, "fingerprint", "{}"));
        verify(eventPublisher).publishUsageRecorded(any(UsageRecordedEvent.class));
    }

    private ModelCallService service(BillingService billingService, GatewayEventPublisher eventPublisher) {
        return new ModelCallService(
                null,
                null,
                null,
                null,
                null,
                null,
                billingService,
                null,
                new ObjectMapper(),
                eventPublisher);
    }

    private RequestLog requestLog() {
        RequestLog requestLog = new RequestLog();
        requestLog.setRequestId("req_1");
        requestLog.setUserId(100L);
        requestLog.setApiKeyId(10L);
        requestLog.setProviderId(1L);
        requestLog.setProviderKeyId(2L);
        requestLog.setModelId(20L);
        requestLog.setStatusCode(200);
        requestLog.setLatencyMs(120);
        requestLog.setCreatedAt(new Date());
        return requestLog;
    }

    private ChatResponse.Usage usage() {
        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(10);
        usage.setCompletionTokens(20);
        usage.setTotalTokens(30);
        return usage;
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
}
