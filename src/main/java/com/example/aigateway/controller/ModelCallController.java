package com.example.aigateway.controller;

import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.security.ApiKeyPrincipal;
import com.example.aigateway.service.ModelCallService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping({"/api/chat", "/v1/chat"})
public class ModelCallController {
    private final ModelCallService modelCallService;

    public ModelCallController(ModelCallService modelCallService) {
        this.modelCallService = modelCallService;
    }

    @PostMapping("/completions")
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication
    ) {
        return modelCallService.chat(request, currentApiKey(authentication));
    }

    @PostMapping(value = "/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication
    ) {
        return modelCallService.stream(request, currentApiKey(authentication));
    }

    private ApiKeyPrincipal currentApiKey(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiKeyPrincipal principal)) {
            throw new BusinessException("UNAUTHORIZED", "API key is required", HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }
}
