package com.example.aigateway.provider.openai;

import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.provider.ProviderStreamEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Parses OpenAI-compatible SSE data into provider-neutral stream events. */
@Component
public class OpenAiStreamEventParser {
    private final ObjectMapper objectMapper;

    public OpenAiStreamEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProviderStreamEvent parse(String data) {
        if (data == null || data.isBlank()) {
            throw streamError("Provider returned an empty stream event");
        }
        if ("[DONE]".equals(data.trim())) {
            return ProviderStreamEvent.done(data);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(data);
        } catch (JsonProcessingException exception) {
            throw streamError("Provider returned invalid stream JSON");
        }
        if (root == null || !root.isObject()) {
            throw streamError("Provider returned an invalid stream event");
        }
        if (root.hasNonNull("error")) {
            String message = root.path("error").path("message").asText("Provider stream failed");
            throw streamError(message);
        }

        JsonNode choice = firstChoice(root.path("choices"));
        JsonNode delta = choice.path("delta");
        String role = textOrNull(delta.path("role"));
        String textDelta = textValueOrNull(delta.path("content"));
        String finishReason = textOrNull(choice.path("finish_reason"));

        return new ProviderStreamEvent(
                data,
                textOrNull(root.path("id")),
                textOrNull(root.path("model")),
                role,
                textDelta,
                finishReason,
                usage(root.path("usage")),
                false);
    }

    private JsonNode firstChoice(JsonNode choices) {
        if (choices.isArray() && !choices.isEmpty()) {
            return choices.get(0);
        }
        return objectMapper.createObjectNode();
    }

    private ChatResponse.Usage usage(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(integerOrNull(node.path("prompt_tokens")));
        usage.setCompletionTokens(integerOrNull(node.path("completion_tokens")));
        usage.setTotalTokens(integerOrNull(node.path("total_tokens")));
        return usage;
    }

    private Integer integerOrNull(JsonNode node) {
        return node.isIntegralNumber() ? node.asInt() : null;
    }

    private String textOrNull(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private String textValueOrNull(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    private BusinessException streamError(String message) {
        return new BusinessException("PROVIDER_STREAM_ERROR", message, HttpStatus.BAD_GATEWAY);
    }
}
