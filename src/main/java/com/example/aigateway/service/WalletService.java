package com.example.aigateway.service;

import com.example.aigateway.entity.Wallet;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.WalletMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WalletService {
    private final WalletMapper walletMapper;

    public WalletService(WalletMapper walletMapper) {
        this.walletMapper = walletMapper;
    }

    public Wallet getWalletByUserId(long userId) {
        Wallet wallet = walletMapper.getWalletByUserId(userId);
        if (wallet == null) {
            throw new BusinessException("WALLET_NOT_FOUND", "Wallet not found", HttpStatus.NOT_FOUND);
        }
        return wallet;
    }
}
