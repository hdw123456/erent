package com.example.aigateway.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aigateway.mapper.UserMapper;
import com.example.aigateway.mapper.WalletMapper;
import com.example.aigateway.entity.UserAccount;
import com.example.aigateway.entity.Wallet;
import java.math.BigDecimal;

@Service
public class UserService {
    private final WalletMapper walletMapper;
    private final UserMapper userMapper;

    public UserService(WalletMapper walletMapper, UserMapper userMapper) {
        this.walletMapper = walletMapper;
        this.userMapper = userMapper;
    }

    private void checkUsernameExists(String username) {
        UserAccount existingUser = userMapper.getUserByUsername(username);
        if (existingUser != null) {
            throw new IllegalArgumentException("Username already exists");
        }
    }

    @Transactional
    public void register(String username, String password, String email) {
        checkUsernameExists(username);
        UserAccount user = new UserAccount(username, email, password);
        userMapper.insertUser(user);

        Wallet wallet = new Wallet(user.getId(), BigDecimal.ZERO);
        walletMapper.insertWallet(wallet);
    }
}
