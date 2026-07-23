package com.example.aigateway.service;

import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.provider.ProviderStreamEvent;

/** Aggregates provider stream deltas and supplies an explicit estimation fallback. */
public class StreamResponseAccumulator {
    public static final String USAGE_SOURCE_PROVIDER = "PROVIDER";
    public static final String USAGE_SOURCE_ESTIMATED = "ESTIMATED";

    private final ChatRequest request;
    private final String requestId;
    private final String defaultModel;
    private final StringBuilder content = new StringBuilder();

    private String responseId;
    private String model;
    private String role = "assistant";
    private String finishReason;
    private ChatResponse.Usage providerUsage;
    private boolean providerEventReceived;

    public StreamResponseAccumulator(ChatRequest request, String requestId, String defaultModel) {
        this.request = request;
        this.requestId = requestId;
        this.defaultModel = defaultModel;
    }

    public void accept(ProviderStreamEvent event) {
        providerEventReceived = true;
        if (hasText(event.responseId())) {
            responseId = event.responseId();
        }
        if (hasText(event.model())) {
            model = event.model();
        }
        if (hasText(event.role())) {
            role = event.role();
        }
        if (event.textDelta() != null) {
            content.append(event.textDelta());
        }
        if (hasText(event.finishReason())) {
            finishReason = event.finishReason();
        }
        if (event.usage() != null) {
            providerUsage = copyUsage(event.usage());
        }
    }

    public boolean hasProviderEvent() {
        return providerEventReceived;
    }

    public Result result() {
        int estimatedInput = estimateInputTokens(request);
        int estimatedOutput = estimateTokens(content.toString());
        boolean estimated = providerUsage == null
                || providerUsage.getPromptTokens() == null
                || providerUsage.getCompletionTokens() == null;

        int inputTokens = valueOr(providerUsage == null ? null : providerUsage.getPromptTokens(), estimatedInput);
        int outputTokens = valueOr(providerUsage == null ? null : providerUsage.getCompletionTokens(), estimatedOutput);
        int totalTokens = valueOr(
                providerUsage == null ? null : providerUsage.getTotalTokens(),
                inputTokens + outputTokens);

        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(inputTokens);
        usage.setCompletionTokens(outputTokens);
        usage.setTotalTokens(totalTokens);

        ChatResponse.Message message = new ChatResponse.Message();
        message.setRole(role);
        message.setContent(content.toString());

        ChatResponse response = new ChatResponse();
        response.setRequestId(requestId);
        response.setId(hasText(responseId) ? responseId : requestId);
        response.setModel(hasText(model) ? model : defaultModel);
        response.setMessage(message);
        response.setFinishReason(hasText(finishReason) ? finishReason : "stop");
        response.setUsage(usage);
        return new Result(response, estimated ? USAGE_SOURCE_ESTIMATED : USAGE_SOURCE_PROVIDER);
    }

    private ChatResponse.Usage copyUsage(ChatResponse.Usage source) {
        ChatResponse.Usage copy = new ChatResponse.Usage();
        copy.setPromptTokens(source.getPromptTokens());
        copy.setCompletionTokens(source.getCompletionTokens());
        copy.setTotalTokens(source.getTotalTokens());
        return copy;
    }

    private int estimateInputTokens(ChatRequest request) {
        if (request == null || request.getMessages() == null) {
            return 0;
        }
        int characters = 0;
        for (ChatRequest.Message message : request.getMessages()) {
            if (message.getRole() != null) {
                characters += message.getRole().length();
            }
            if (message.getContent() != null) {
                characters += message.getContent().length();
            }
        }
        return estimateTokens(characters);
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : estimateTokens(text.length());
    }

    private int estimateTokens(int characters) {
        return characters <= 0 ? 0 : Math.max(1, (characters + 3) / 4);
    }

    private int valueOr(Integer value, int fallback) {
        return value == null ? fallback : Math.max(0, value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Result(ChatResponse response, String usageSource) {
    }
}
