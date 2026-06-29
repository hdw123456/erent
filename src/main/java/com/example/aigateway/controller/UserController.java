package com.example.aigateway.controller;

import com.example.aigateway.dto.response.UserResponse;
import com.example.aigateway.entity.UserAccount;
import com.example.aigateway.service.CurrentUserService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final CurrentUserService currentUserService;

    public UserController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser() {
        UserAccount userAccount = currentUserService.getCurrentUser();
        UserResponse userResponse = new UserResponse();
        userResponse.setId(userAccount.getId());
        userResponse.setUsername(userAccount.getUsername());
        userResponse.setEmail(userAccount.getEmail());
        userResponse.setEnabled(userAccount.isEnabled());
        userResponse.setCreatedAt(userAccount.getCreatedAt() == null ? null : userAccount.getCreatedAt().toString());
        return userResponse;
    }
}
