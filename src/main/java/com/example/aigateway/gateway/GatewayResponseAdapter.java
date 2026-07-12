package com.example.aigateway.gateway;

import com.example.aigateway.dto.ProviderModelPricing;
import com.example.aigateway.dto.response.ChatResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Formats non-streaming responses for public gateway protocols. */
@Component
public class GatewayResponseAdapter {
    public Map<String, Object> toOpenAiChatCompletion(ChatResponse response) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", responseId(response));
        body.put("object", "chat.completion");
        body.put("created", Instant.now().getEpochSecond());
        body.put("model", response.getModel());

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", Map.of(
                "role", responseRole(response),
                "content", responseContent(response)
        ));
        choice.put("finish_reason", response.getFinishReason() == null ? "stop" : response.getFinishReason());
        body.put("choices", List.of(choice));
        body.put("usage", openAiUsage(response.getUsage()));
        return body;
    }

    public Map<String, Object> toAnthropicMessage(ChatResponse response) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", responseId(response));
        body.put("type", "message");
        body.put("role", "assistant");
        body.put("model", response.getModel());
        body.put("content", List.of(Map.of(
                "type", "text",
                "text", responseContent(response)
        )));
        body.put("stop_reason", anthropicStopReason(response.getFinishReason()));
        body.put("stop_sequence", null);
        body.put("usage", Map.of(
                "input_tokens", token(response.getUsage() == null ? null : response.getUsage().getPromptTokens()),
                "output_tokens", token(response.getUsage() == null ? null : response.getUsage().getCompletionTokens())
        ));
        return body;
    }

    public Map<String, Object> toResponses(ChatResponse response) {
        String content = responseContent(response);
        Map<String, Object> contentItem = new LinkedHashMap<>();
        contentItem.put("type", "output_text");
        contentItem.put("text", content);
        contentItem.put("annotations", List.of());

        Map<String, Object> outputMessage = new LinkedHashMap<>();
        outputMessage.put("id", "msg_" + responseId(response));
        outputMessage.put("type", "message");
        outputMessage.put("status", "completed");
        outputMessage.put("role", "assistant");
        outputMessage.put("content", List.of(contentItem));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", responseId(response));
        body.put("object", "response");
        body.put("created_at", Instant.now().getEpochSecond());
        body.put("status", "completed");
        body.put("model", response.getModel());
        body.put("output", List.of(outputMessage));
        body.put("output_text", content);
        body.put("usage", responsesUsage(response.getUsage()));
        return body;
    }

    public Map<String, Object> toModels(List<ProviderModelPricing> models) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (ProviderModelPricing model : models) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", model.getModelCode());
            item.put("object", "model");
            item.put("created", 0);
            item.put("owned_by", model.getProviderCode());
            data.add(item);
        }
        return Map.of(
                "object", "list",
                "data", data
        );
    }

    public Map<String, Object> countTokens(int inputTokens) {
        return Map.of("input_tokens", inputTokens);
    }

    private Map<String, Object> openAiUsage(ChatResponse.Usage usage) {
        return Map.of(
                "prompt_tokens", token(usage == null ? null : usage.getPromptTokens()),
                "completion_tokens", token(usage == null ? null : usage.getCompletionTokens()),
                "total_tokens", token(usage == null ? null : usage.getTotalTokens())
        );
    }

    private Map<String, Object> responsesUsage(ChatResponse.Usage usage) {
        return Map.of(
                "input_tokens", token(usage == null ? null : usage.getPromptTokens()),
                "output_tokens", token(usage == null ? null : usage.getCompletionTokens()),
                "total_tokens", token(usage == null ? null : usage.getTotalTokens())
        );
    }

    private String responseId(ChatResponse response) {
        if (response.getId() != null && !response.getId().isBlank()) {
            return response.getId();
        }
        return response.getRequestId();
    }

    private String responseRole(ChatResponse response) {
        if (response.getMessage() != null
                && response.getMessage().getRole() != null
                && !response.getMessage().getRole().isBlank()) {
            return response.getMessage().getRole();
        }
        return "assistant";
    }

    private String responseContent(ChatResponse response) {
        if (response.getMessage() == null || response.getMessage().getContent() == null) {
            return "";
        }
        return response.getMessage().getContent();
    }

    private String anthropicStopReason(String finishReason) {
        if (finishReason == null || finishReason.isBlank() || "stop".equals(finishReason)) {
            return "end_turn";
        }
        if ("length".equals(finishReason)) {
            return "max_tokens";
        }
        if ("tool_calls".equals(finishReason)) {
            return "tool_use";
        }
        return finishReason;
    }

    private int token(Integer value) {
        return value == null ? 0 : value;
    }
}
