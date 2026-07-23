package com.example.aigateway.service;

import com.example.aigateway.entity.IdempotencyRecord;
import com.example.aigateway.entity.RequestLog;
import com.example.aigateway.entity.UsageBillingDedup;
import com.example.aigateway.entity.UsageRecord;
import com.example.aigateway.entity.Wallet;
import com.example.aigateway.entity.WalletTransaction;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.IdempotencyRecordMapper;
import com.example.aigateway.mapper.RequestLogMapper;
import com.example.aigateway.mapper.UsageRecordMapper;
import com.example.aigateway.mapper.UsageBillingDedupMapper;
import com.example.aigateway.mapper.WalletMapper;
import com.example.aigateway.mapper.WalletTransactionMapper;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the atomic usage, wallet, transaction, request-log, and idempotency write. */
@Service
public class BillingService {
    private static final String TRANSACTION_TYPE_USAGE_DEDUCT = "USAGE_DEDUCT";
    private static final String TRANSACTION_TYPE_PRE_DEDUCT = "PRE_DEDUCT";
    private static final String IDEMPOTENCY_STATUS_COMPLETED = "COMPLETED";
    private static final String IDEMPOTENCY_STATUS_FAILED = "FAILED";

    private final WalletMapper walletMapper;
    private final UsageRecordMapper usageRecordMapper;
    private final WalletTransactionMapper walletTransactionMapper;
    private final RequestLogMapper requestLogMapper;
    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final UsageBillingDedupMapper usageBillingDedupMapper;

    public BillingService(WalletMapper walletMapper,
            UsageRecordMapper usageRecordMapper,
            WalletTransactionMapper walletTransactionMapper,
            RequestLogMapper requestLogMapper,
            IdempotencyRecordMapper idempotencyRecordMapper,
            UsageBillingDedupMapper usageBillingDedupMapper) {
        this.walletMapper = walletMapper;
        this.usageRecordMapper = usageRecordMapper;
        this.walletTransactionMapper = walletTransactionMapper;
        this.requestLogMapper = requestLogMapper;
        this.idempotencyRecordMapper = idempotencyRecordMapper;
        this.usageBillingDedupMapper = usageBillingDedupMapper;
    }

    /** Commits successful usage and the completed idempotency result in one transaction. */
    @Transactional
    public boolean recordSuccessfulUsage(RequestLog successLog, UsageRecord usageRecord, Long idempotencyRecordId,
            String requestFingerprint, String idempotencyResponseJson) {
        return recordChargedUsage(
                successLog,
                usageRecord,
                idempotencyRecordId,
                requestFingerprint,
                IDEMPOTENCY_STATUS_COMPLETED,
                idempotencyResponseJson,
                null);
    }

    /**
     * Charges provider work already consumed before a stream failed or the client disconnected.
     * The idempotency record remains failed, so a partial response is never replayed as success.
     */
    @Transactional
    public void recordPartialUsage(
            RequestLog requestLog,
            UsageRecord usageRecord,
            Long idempotencyRecordId,
            String requestFingerprint,
            String errorCode) {
        recordChargedUsage(
                requestLog,
                usageRecord,
                idempotencyRecordId,
                requestFingerprint,
                IDEMPOTENCY_STATUS_FAILED,
                null,
                errorCode);
    }

    /** Records a terminal failure without creating usage or changing the wallet. */
    @Transactional
    public void recordFailedRequestWithoutCharge(RequestLog failedLog, Long idempotencyRecordId, String errorCode) {
        requestLogMapper.insertRequestLog(failedLog);
        if (idempotencyRecordId != null) {
            idempotencyRecordMapper.updateIdempotencyRecordResult(
                    idempotencyRecordId, IDEMPOTENCY_STATUS_FAILED, null, errorCode);
        }
    }

    public void ensureWalletCanStartCall(Long userId) {
        if (userId == null) {
            throw new BusinessException("USER_ID_REQUIRED", "User id is required for billing");
        }

        Wallet wallet = walletMapper.getWalletByUserId(userId);
        if (wallet == null) {
            throw new BusinessException("WALLET_NOT_FOUND", "Wallet not found for user: " + userId,
                    HttpStatus.NOT_FOUND);
        }
        if (wallet.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INSUFFICIENT_BALANCE", "Wallet balance is not enough", HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public void preDeductThenRollbackOnFailure(Long userId, BigDecimal estimatedCost, String requestId) {
        BigDecimal costAmount = requireValidCost(estimatedCost);
        Wallet wallet = lockWallet(userId);
        BigDecimal balanceAfter = deduct(wallet, costAmount);

        walletTransactionMapper.insertWalletTransaction(new WalletTransaction(
                wallet.getId(),
                TRANSACTION_TYPE_PRE_DEDUCT,
                costAmount.negate(),
                balanceAfter,
                requestId));

        throw new BusinessException(
                "PROVIDER_CALL_FAILED",
                "Provider call failed, pre-deduct transaction rolled back",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** Locks the single wallet row that serializes concurrent balance deductions. */
    private Wallet lockWallet(Long userId) {
        if (userId == null) {
            throw new BusinessException("USER_ID_REQUIRED", "User id is required for billing");
        }

        Wallet wallet = walletMapper.getWalletByUserIdForUpdate(userId);
        if (wallet == null) {
            throw new BusinessException("WALLET_NOT_FOUND", "Wallet not found for user: " + userId,
                    HttpStatus.NOT_FOUND);
        }
        return wallet;
    }

    private BigDecimal deduct(Wallet wallet, BigDecimal costAmount) {
        if (wallet.getBalance().compareTo(costAmount) < 0) {
            throw new BusinessException("INSUFFICIENT_BALANCE", "Wallet balance is not enough", HttpStatus.CONFLICT);
        }

        BigDecimal balanceAfter = wallet.getBalance().subtract(costAmount);
        walletMapper.updateBalance(wallet.getUserId(), balanceAfter);
        return balanceAfter;
    }

    private BigDecimal requireValidCost(BigDecimal costAmount) {
        if (costAmount == null || costAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_COST_AMOUNT", "Cost amount must be greater than or equal to zero");
        }
        return costAmount;
    }

    private boolean recordChargedUsage(
            RequestLog requestLog,
            UsageRecord usageRecord,
            Long idempotencyRecordId,
            String requestFingerprint,
            String idempotencyStatus,
            String idempotencyResponseJson,
            String errorCode) {
        if (!claimUsageBillingDedup(requestLog, requestFingerprint)) {
            return false;
        }

        BigDecimal costAmount = requireValidCost(usageRecord.getCostAmount());
        Wallet wallet = lockWallet(usageRecord.getUserId());
        BigDecimal balanceAfter = deduct(wallet, costAmount);

        requestLogMapper.insertRequestLog(requestLog);
        usageRecordMapper.insertUsageRecord(usageRecord);
        walletTransactionMapper.insertWalletTransaction(new WalletTransaction(
                wallet.getId(),
                TRANSACTION_TYPE_USAGE_DEDUCT,
                costAmount.negate(),
                balanceAfter,
                usageRecord.getRequestId()));
        if (idempotencyRecordId != null) {
            idempotencyRecordMapper.updateIdempotencyRecordResult(
                    idempotencyRecordId,
                    idempotencyStatus,
                    idempotencyResponseJson,
                    errorCode);
        }
        return true;
    }

    private boolean claimUsageBillingDedup(RequestLog requestLog, String requestFingerprint) {
        if (requestLog == null || requestLog.getRequestId() == null || requestLog.getRequestId().isBlank()) {
            throw new BusinessException("REQUEST_ID_REQUIRED", "Request id is required for billing");
        }
        if (requestLog.getApiKeyId() == null) {
            throw new BusinessException("API_KEY_ID_REQUIRED", "API key id is required for billing");
        }
        if (requestFingerprint == null || requestFingerprint.isBlank()) {
            throw new BusinessException(
                    "BILLING_FINGERPRINT_REQUIRED",
                    "Request fingerprint is required for billing",
                    HttpStatus.CONFLICT);
        }

        UsageBillingDedup billingDedup = new UsageBillingDedup(
                requestLog.getRequestId(), requestLog.getApiKeyId(), requestFingerprint);
        int inserted = usageBillingDedupMapper.insertUsageBillingDedupIgnore(billingDedup);
        if (inserted == 1) {
            return true;
        }

        UsageBillingDedup existingBillingDedup = usageBillingDedupMapper.getByRequestIdAndApiKeyIdForUpdate(
                billingDedup.getRequestId(), billingDedup.getApiKeyId());
        if (existingBillingDedup == null) {
            throw new BusinessException(
                    "BILLING_DEDUP_LOOKUP_FAILED",
                    "Failed to load existing billing dedup record",
                    HttpStatus.CONFLICT);
        }
        if (!existingBillingDedup.getRequestFingerprint().equals(requestFingerprint)) {
            throw new BusinessException(
                    "BILLING_FINGERPRINT_CONFLICT",
                    "Request id was already used with a different billing fingerprint",
                    HttpStatus.CONFLICT);
        }
        return false;
    }
}
