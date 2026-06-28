package com.example.aigateway.service;

import com.example.aigateway.entity.RequestLog;
import com.example.aigateway.entity.UsageRecord;
import com.example.aigateway.entity.Wallet;
import com.example.aigateway.entity.WalletTransaction;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.RequestLogMapper;
import com.example.aigateway.mapper.UsageRecordMapper;
import com.example.aigateway.mapper.WalletMapper;
import com.example.aigateway.mapper.WalletTransactionMapper;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
    private static final String TRANSACTION_TYPE_USAGE_DEDUCT = "USAGE_DEDUCT";
    private static final String TRANSACTION_TYPE_PRE_DEDUCT = "PRE_DEDUCT";

    private final WalletMapper walletMapper;
    private final UsageRecordMapper usageRecordMapper;
    private final WalletTransactionMapper walletTransactionMapper;
    private final RequestLogMapper requestLogMapper;

    public BillingService(WalletMapper walletMapper,
                          UsageRecordMapper usageRecordMapper,
                          WalletTransactionMapper walletTransactionMapper,
                          RequestLogMapper requestLogMapper) {
        this.walletMapper = walletMapper;
        this.usageRecordMapper = usageRecordMapper;
        this.walletTransactionMapper = walletTransactionMapper;
        this.requestLogMapper = requestLogMapper;
    }

    @Transactional
    public void recordSuccessfulUsage(RequestLog successLog, UsageRecord usageRecord) {
        BigDecimal costAmount = requireValidCost(usageRecord.getCostAmount());
        Wallet wallet = lockWallet(usageRecord.getUserId());
        BigDecimal balanceAfter = deduct(wallet, costAmount);

        requestLogMapper.insertRequestLog(successLog);
        usageRecordMapper.insertUsageRecord(usageRecord);
        walletTransactionMapper.insertWalletTransaction(new WalletTransaction(
                wallet.getId(),
                TRANSACTION_TYPE_USAGE_DEDUCT,
                costAmount.negate(),
                balanceAfter,
                usageRecord.getRequestId()
        ));
    }

    @Transactional
    public void recordFailedRequestWithoutCharge(RequestLog failedLog) {
        requestLogMapper.insertRequestLog(failedLog);
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
                requestId
        ));

        throw new BusinessException(
                "PROVIDER_CALL_FAILED",
                "Provider call failed, pre-deduct transaction rolled back",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    private Wallet lockWallet(Long userId) {
        if (userId == null) {
            throw new BusinessException("USER_ID_REQUIRED", "User id is required for billing");
        }

        Wallet wallet = walletMapper.getWalletByUserIdForUpdate(userId);
        if (wallet == null) {
            throw new BusinessException("WALLET_NOT_FOUND", "Wallet not found for user: " + userId, HttpStatus.NOT_FOUND);
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
}
