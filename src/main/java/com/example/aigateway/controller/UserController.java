package com.example.aigateway.controller;

import com.example.aigateway.dto.response.UserResponse;
import com.example.aigateway.entity.UserAccount;
import com.example.aigateway.service.UserService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(@RequestHeader("X-User-Id") Long userId) {
        UserAccount userAccount = userService.getUserById(userId);
        UserResponse userResponse = new UserResponse();
        userResponse.setId(userAccount.getId());
        userResponse.setUsername(userAccount.getUsername());
        userResponse.setEmail(userAccount.getEmail());
        userResponse.setEnabled(userAccount.isEnabled());
        userResponse.setCreatedAt(userAccount.getCreatedAt().toString());
        return userResponse;
    }
}
