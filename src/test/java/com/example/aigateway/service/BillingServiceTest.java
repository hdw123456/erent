package com.example.aigateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.aigateway.entity.RequestLog;
import com.example.aigateway.entity.UsageBillingDedup;
import com.example.aigateway.entity.UsageRecord;
import com.example.aigateway.entity.Wallet;
import com.example.aigateway.entity.WalletTransaction;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.IdempotencyRecordMapper;
import com.example.aigateway.mapper.RequestLogMapper;
import com.example.aigateway.mapper.UsageBillingDedupMapper;
import com.example.aigateway.mapper.UsageRecordMapper;
import com.example.aigateway.mapper.WalletMapper;
import com.example.aigateway.mapper.WalletTransactionMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BillingServiceTest {

    @Test
    void recordSuccessfulUsageShouldClaimBillingDedupBeforeCharging() {
        Fixture fixture = new Fixture();
        RequestLog requestLog = requestLog();
        UsageRecord usageRecord = usageRecord();
        Wallet wallet = wallet();
        when(fixture.usageBillingDedupMapper.insertUsageBillingDedupIgnore(any())).thenReturn(1);
        when(fixture.walletMapper.getWalletByUserIdForUpdate(100L)).thenReturn(wallet);

        fixture.service.recordSuccessfulUsage(requestLog, usageRecord, null, "fingerprint-a", "{}");

        ArgumentCaptor<UsageBillingDedup> captor = ArgumentCaptor.forClass(UsageBillingDedup.class);
        verify(fixture.usageBillingDedupMapper).insertUsageBillingDedupIgnore(captor.capture());
        UsageBillingDedup dedup = captor.getValue();
        assertEquals("req_1", dedup.getRequestId());
        assertEquals(10L, dedup.getApiKeyId());
        assertEquals("fingerprint-a", dedup.getRequestFingerprint());
        verify(fixture.walletMapper).updateBalance(100L, new BigDecimal("9.25"));
        verify(fixture.requestLogMapper).insertRequestLog(requestLog);
        verify(fixture.usageRecordMapper).insertUsageRecord(usageRecord);
        verify(fixture.walletTransactionMapper).insertWalletTransaction(any(WalletTransaction.class));
    }

    @Test
    void recordSuccessfulUsageShouldSkipChargeWhenBillingDedupAlreadyExistsWithSameFingerprint() {
        Fixture fixture = new Fixture();
        when(fixture.usageBillingDedupMapper.insertUsageBillingDedupIgnore(any())).thenReturn(0);
        when(fixture.usageBillingDedupMapper.getByRequestIdAndApiKeyIdForUpdate("req_1", 10L))
                .thenReturn(new UsageBillingDedup("req_1", 10L, "fingerprint-a"));

        fixture.service.recordSuccessfulUsage(requestLog(), usageRecord(), null, "fingerprint-a", "{}");

        verify(fixture.usageBillingDedupMapper).getByRequestIdAndApiKeyIdForUpdate("req_1", 10L);
        verifyNoInteractions(
                fixture.walletMapper,
                fixture.requestLogMapper,
                fixture.usageRecordMapper,
                fixture.walletTransactionMapper,
                fixture.idempotencyRecordMapper);
    }

    @Test
    void recordSuccessfulUsageShouldRejectSameRequestIdWithDifferentFingerprint() {
        Fixture fixture = new Fixture();
        when(fixture.usageBillingDedupMapper.insertUsageBillingDedupIgnore(any())).thenReturn(0);
        when(fixture.usageBillingDedupMapper.getByRequestIdAndApiKeyIdForUpdate("req_1", 10L))
                .thenReturn(new UsageBillingDedup("req_1", 10L, "fingerprint-b"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fixture.service.recordSuccessfulUsage(
                        requestLog(), usageRecord(), null, "fingerprint-a", "{}"));

        assertEquals("BILLING_FINGERPRINT_CONFLICT", exception.getCode());
        verify(fixture.walletMapper, never()).getWalletByUserIdForUpdate(100L);
        verifyNoInteractions(
                fixture.requestLogMapper,
                fixture.usageRecordMapper,
                fixture.walletTransactionMapper,
                fixture.idempotencyRecordMapper);
    }

    private RequestLog requestLog() {
        RequestLog requestLog = new RequestLog();
        requestLog.setRequestId("req_1");
        requestLog.setUserId(100L);
        requestLog.setApiKeyId(10L);
        requestLog.setModelId(20L);
        requestLog.setStatusCode(200);
        return requestLog;
    }

    private UsageRecord usageRecord() {
        return new UsageRecord(
                "req_1",
                100L,
                20L,
                100,
                50,
                150,
                new BigDecimal("0.75"));
    }

    private Wallet wallet() {
        Wallet wallet = new Wallet(100L, new BigDecimal("10.00"));
        wallet.setId(200L);
        return wallet;
    }

    private static final class Fixture {
        private final WalletMapper walletMapper = org.mockito.Mockito.mock(WalletMapper.class);
        private final UsageRecordMapper usageRecordMapper = org.mockito.Mockito.mock(UsageRecordMapper.class);
        private final WalletTransactionMapper walletTransactionMapper =
                org.mockito.Mockito.mock(WalletTransactionMapper.class);
        private final RequestLogMapper requestLogMapper = org.mockito.Mockito.mock(RequestLogMapper.class);
        private final IdempotencyRecordMapper idempotencyRecordMapper =
                org.mockito.Mockito.mock(IdempotencyRecordMapper.class);
        private final UsageBillingDedupMapper usageBillingDedupMapper =
                org.mockito.Mockito.mock(UsageBillingDedupMapper.class);
        private final BillingService service = new BillingService(
                walletMapper,
                usageRecordMapper,
                walletTransactionMapper,
                requestLogMapper,
                idempotencyRecordMapper,
                usageBillingDedupMapper);
    }
}
