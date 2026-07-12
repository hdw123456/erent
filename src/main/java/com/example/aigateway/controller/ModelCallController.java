package com.example.aigateway.controller;

import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.gateway.GatewayRequestAdapter;
import com.example.aigateway.gateway.GatewayResponseAdapter;
import com.example.aigateway.gateway.stream.GatewayStreamProtocol;
import com.example.aigateway.security.ApiKeyPrincipal;
import com.example.aigateway.service.ModelCallService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Accepts internal and provider-compatible model-call protocols. */
@RestController
public class ModelCallController {
    private final ModelCallService modelCallService;
    private final GatewayRequestAdapter gatewayRequestAdapter;
    private final GatewayResponseAdapter gatewayResponseAdapter;

    public ModelCallController(
            ModelCallService modelCallService,
            GatewayRequestAdapter gatewayRequestAdapter,
            GatewayResponseAdapter gatewayResponseAdapter
    ) {
        this.modelCallService = modelCallService;
        this.gatewayRequestAdapter = gatewayRequestAdapter;
        this.gatewayResponseAdapter = gatewayResponseAdapter;
    }

    @PostMapping("/api/chat/completions")
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return modelCallService.chat(request, currentApiKey(authentication), idempotencyKey);
    }

    @PostMapping(value = "/api/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return modelCallService.stream(request, currentApiKey(authentication), idempotencyKey);
    }

    @PostMapping({"/v1/chat/completions", "/chat/completions"})
    public ResponseEntity<?> chatCompletions(
            @RequestBody String rawBody,
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        ApiKeyPrincipal principal = currentApiKey(authentication);
        ChatRequest request = gatewayRequestAdapter.fromChatCompletions(rawBody);
        if (request.isStream()) {
            return streamResponse(modelCallService.stream(
                    request,
                    principal,
                    idempotencyKey,
                    "chat_completions",
                    rawBody,
                    GatewayStreamProtocol.OPENAI_CHAT_COMPLETIONS));
        }

        ChatResponse response = modelCallService.chat(
                request,
                principal,
                idempotencyKey,
                "chat_completions",
                rawBody
        );
        return ResponseEntity.ok(gatewayResponseAdapter.toOpenAiChatCompletion(response));
    }

    @PostMapping("/v1/messages")
    public ResponseEntity<?> messages(
            @RequestBody String rawBody,
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        ApiKeyPrincipal principal = currentApiKey(authentication);
        ChatRequest request = gatewayRequestAdapter.fromAnthropicMessages(rawBody);
        if (request.isStream()) {
            return streamResponse(modelCallService.stream(
                    request,
                    principal,
                    idempotencyKey,
                    "messages",
                    rawBody,
                    GatewayStreamProtocol.ANTHROPIC_MESSAGES));
        }

        ChatResponse response = modelCallService.chat(
                request,
                principal,
                idempotencyKey,
                "messages",
                rawBody
        );
        return ResponseEntity.ok(gatewayResponseAdapter.toAnthropicMessage(response));
    }

    @PostMapping("/v1/messages/count_tokens")
    public ResponseEntity<?> countTokens(@RequestBody String rawBody, Authentication authentication) {
        currentApiKey(authentication);
        return ResponseEntity.ok(gatewayResponseAdapter.countTokens(gatewayRequestAdapter.estimateInputTokens(rawBody)));
    }

    @PostMapping({
            "/v1/responses",
            "/v1/responses/**",
            "/responses",
            "/responses/**",
            "/backend-api/codex/responses",
            "/backend-api/codex/responses/**"
    })
    public ResponseEntity<?> responses(
            @RequestBody String rawBody,
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        ApiKeyPrincipal principal = currentApiKey(authentication);
        ChatRequest request = gatewayRequestAdapter.fromResponses(rawBody);
        if (request.isStream()) {
            return streamResponse(modelCallService.stream(
                    request,
                    principal,
                    idempotencyKey,
                    "responses",
                    rawBody,
                    GatewayStreamProtocol.OPENAI_RESPONSES));
        }

        ChatResponse response = modelCallService.chat(
                request,
                principal,
                idempotencyKey,
                "responses",
                rawBody
        );
        return ResponseEntity.ok(gatewayResponseAdapter.toResponses(response));
    }

    @GetMapping({
            "/responses",
            "/v1/responses",
            "/backend-api/codex/responses"
    })
    public ResponseEntity<?> responsesWebsocketProbe(Authentication authentication) {
        currentApiKey(authentication);
        throw new BusinessException(
                "WEBSOCKET_UPGRADE_REQUIRED",
                "Use a WebSocket upgrade request for the Responses WebSocket endpoint",
                HttpStatus.UPGRADE_REQUIRED
        );
    }

    private ApiKeyPrincipal currentApiKey(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiKeyPrincipal principal)) {
            throw new BusinessException("UNAUTHORIZED", "API key is required", HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }

    private ResponseEntity<SseEmitter> streamResponse(SseEmitter emitter) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

}
