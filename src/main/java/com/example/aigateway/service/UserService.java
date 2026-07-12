package com.example.aigateway.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aigateway.mapper.UserMapper;
import com.example.aigateway.mapper.WalletMapper;
import com.example.aigateway.entity.UserAccount;
import com.example.aigateway.entity.Wallet;
import com.example.aigateway.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;

import java.math.BigDecimal;

/** Registers users and exposes account profile data. */
@Service
public class UserService {
    private final WalletMapper walletMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final static Logger logger = LoggerFactory.getLogger(UserService.class);

    public UserService(WalletMapper walletMapper, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.walletMapper = walletMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    private void checkUsernameExists(String username) {
        UserAccount existingUser = userMapper.getUserByUsername(username);
        if (existingUser != null) {
            logger.warn("Attempted to register with existing username: {}", username);
            throw new BusinessException("USERNAME_EXISTS", "Username already exists", HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public void register(String username, String password, String email) {
        checkUsernameExists(username);
        UserAccount user = new UserAccount(username, email, passwordEncoder.encode(password));
        userMapper.insertUser(user);

        userMapper.insertUserRole(user.getId(), "USER"); // Assign default role

        Wallet wallet = new Wallet(user.getId(), BigDecimal.ZERO);
        walletMapper.insertWallet(wallet);
        logger.info("User registered successfully; Username={} , UserID={}", username, user.getId());
    }

    public Object getUserApiKeyByUserId(long userId) {
        return userMapper.getUserApiKeyByUserId(userId);
    }

    public UserAccount getUserById(long userId) {
        UserAccount userAccount = userMapper.getUserById(userId);
        if (userAccount == null) {
            logger.warn("User not found, userId={}", userId);
            throw new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND);
        }
        return userAccount;
    }

    public UserAccount getUserByUsername(String username) {
        UserAccount userAccount = userMapper.getUserByUsername(username);
        if (userAccount == null) {
            logger.warn("User not found, username={}", username);
            throw new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND);
        }
        return userAccount;
    }

    public List<String> getRole(long userId) {
        return userMapper.getRole(userId);
    }
}
