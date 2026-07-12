package com.example.aigateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.example.aigateway.entity.Wallet;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.WalletMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Verifies wallet service behavior. */
class WalletServiceTest {

    @Test
    void getWalletByUserIdShouldReturnWallet() {
        WalletMapper walletMapper = org.mockito.Mockito.mock(WalletMapper.class);
        Wallet wallet = new Wallet(1L, BigDecimal.TEN);
        wallet.setId(2L);
        when(walletMapper.getWalletByUserId(1L)).thenReturn(wallet);

        WalletService walletService = new WalletService(walletMapper);

        Wallet result = walletService.getWalletByUserId(1L);

        assertEquals(2L, result.getId());
        assertEquals(1L, result.getUserId());
        assertEquals(BigDecimal.TEN, result.getBalance());
    }

    @Test
    void getWalletByUserIdShouldReturnNotFoundWhenWalletDoesNotExist() {
        WalletMapper walletMapper = org.mockito.Mockito.mock(WalletMapper.class);
        when(walletMapper.getWalletByUserId(1L)).thenReturn(null);

        WalletService walletService = new WalletService(walletMapper);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> walletService.getWalletByUserId(1L)
        );
        assertEquals("WALLET_NOT_FOUND", exception.getCode());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }
}
