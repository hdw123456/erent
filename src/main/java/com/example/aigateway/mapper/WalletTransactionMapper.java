package com.example.aigateway.mapper;

import com.example.aigateway.entity.WalletTransaction;

/** MyBatis persistence operations for wallet transaction data. */
public interface WalletTransactionMapper {
    void insertWalletTransaction(WalletTransaction walletTransaction);
}
