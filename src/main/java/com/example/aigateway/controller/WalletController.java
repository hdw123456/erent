package com.example.aigateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.aigateway.service.WalletService;
import com.example.aigateway.service.CurrentUserService;
import com.example.aigateway.entity.Wallet;
import com.example.aigateway.dto.response.WalletResponse;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {
    private final WalletService walletService;
    private final CurrentUserService currentUserService;

    public WalletController(WalletService walletService, CurrentUserService currentUserService) {
        this.walletService = walletService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    public WalletResponse getWalletByUserId() {
        Wallet wallet = walletService.getWalletByUserId(currentUserService.getCurrentUserId());
        return toResponse(wallet);
    }


    private WalletResponse toResponse(Wallet wallet) {
        WalletResponse response = new WalletResponse();
        response.setId(wallet.getId());
        response.setUserId(wallet.getUserId());
        response.setBalance(wallet.getBalance());
        return response;
    }
}
