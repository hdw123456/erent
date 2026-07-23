package com.example.aigateway.gateway;

import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Normalizes OpenAI, Anthropic, and Responses request bodies. */
@Component
public class GatewayRequestAdapter {
    private final ObjectMapper objectMapper;

    public GatewayRequestAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Preserves the OpenAI payload while extracting fields required for routing and billing. */
    public ChatRequest fromChatCompletions(String rawBody) {
        JsonNode root = readObject(rawBody);
        ChatRequest request = baseRequest(root);
        request.setMessages(readMessages(root.path("messages")));
        request.setStream(root.path("stream").asBoolean(false));
        applyMaxTokens(root, request, "max_tokens", "max_completion_tokens", "maxTokens");
        request.setOpenAiPayload((ObjectNode) root.deepCopy());
        return request;
    }

    /** Converts Anthropic system and message content into the internal chat model. */
    public ChatRequest fromAnthropicMessages(String rawBody) {
        JsonNode root = readObject(rawBody);
        ChatRequest request = baseRequest(root);
        List<ChatRequest.Message> messages = new ArrayList<>();

        JsonNode system = root.path("system");
        if (!system.isMissingNode() && !system.isNull()) {
            String systemText = contentText(system);
            if (!systemText.isBlank()) {
                messages.add(message("system", systemText));
            }
        }

        messages.addAll(readMessages(root.path("messages")));
        if (messages.isEmpty()) {
            throw badRequest("messages is required");
        }
        request.setMessages(messages);
        request.setStream(root.path("stream").asBoolean(false));
        applyMaxTokens(root, request, "max_tokens", "maxTokens");
        return request;
    }

    /** Converts Responses instructions and input items into the internal chat model. */
    public ChatRequest fromResponses(String rawBody) {
        JsonNode root = readObject(rawBody);
        ChatRequest request = baseRequest(root);
        List<ChatRequest.Message> messages = new ArrayList<>();

        JsonNode instructions = root.path("instructions");
        if (!instructions.isMissingNode() && !instructions.isNull()) {
            String instructionText = contentText(instructions);
            if (!instructionText.isBlank()) {
                messages.add(message("system", instructionText));
            }
        }

        JsonNode input = root.path("input");
        if (input.isMissingNode()) {
            input = root.path("messages");
        }
        messages.addAll(readResponsesInput(input));

        if (messages.isEmpty()) {
            throw badRequest("input is required");
        }
        request.setMessages(messages);
        request.setStream(root.path("stream").asBoolean(false));
        applyMaxTokens(root, request, "max_output_tokens", "max_tokens", "maxTokens");
        return request;
    }

    public boolean isStream(String rawBody) {
        return readObject(rawBody).path("stream").asBoolean(false);
    }

    public int estimateInputTokens(String rawBody) {
        JsonNode root = readObject(rawBody);
        String text = collectText(root);
        if (text.isBlank()) {
            return 0;
        }
        return Math.max(1, (text.length() + 3) / 4);
    }

    private ChatRequest baseRequest(JsonNode root) {
        ChatRequest request = new ChatRequest();
        request.setProviderCode(firstText(root, "providerCode", "provider_code", "provider"));
        request.setModel(requiredText(root, "model"));
        request.setTemperature(doubleValue(root.path("temperature")));
        return request;
    }

    private List<ChatRequest.Message> readMessages(JsonNode messagesNode) {
        if (!messagesNode.isArray()) {
            throw badRequest("messages must be an array");
        }

        List<ChatRequest.Message> messages = new ArrayList<>();
        for (JsonNode item : messagesNode) {
            if (!item.isObject()) {
                continue;
            }
            String role = normalizeRole(item.path("role").asText("user"));
            String content = contentText(item.path("content"));
            if (!content.isBlank() || "assistant".equals(role)) {
                messages.add(message(role, content));
            }
        }
        if (messages.isEmpty()) {
            throw badRequest("messages is required");
        }
        return messages;
    }

    private List<ChatRequest.Message> readResponsesInput(JsonNode inputNode) {
        List<ChatRequest.Message> messages = new ArrayList<>();
        if (inputNode.isTextual()) {
            messages.add(message("user", inputNode.asText()));
            return messages;
        }
        if (!inputNode.isArray()) {
            return messages;
        }

        for (JsonNode item : inputNode) {
            if (item.isTextual()) {
                messages.add(message("user", item.asText()));
                continue;
            }
            if (!item.isObject()) {
                continue;
            }
            String role = normalizeRole(item.path("role").asText("user"));
            JsonNode contentNode = item.path("content");
            if (contentNode.isMissingNode()) {
                contentNode = item.path("text");
            }
            String content = contentText(contentNode);
            if (!content.isBlank() || "assistant".equals(role)) {
                messages.add(message(role, content));
            }
        }
        return messages;
    }

    private void applyMaxTokens(JsonNode root, ChatRequest request, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.path(fieldName);
            if (node.isIntegralNumber()) {
                request.setMaxTokens(node.asInt());
                return;
            }
        }
    }

    private String contentText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : node) {
                String text = contentText(item);
                if (!text.isBlank()) {
                    parts.add(text);
                }
            }
            return String.join("\n", parts);
        }
        if (node.isObject()) {
            JsonNode text = firstNode(node, "text", "input_text", "output_text");
            if (text != null && !text.isMissingNode()) {
                return contentText(text);
            }
            JsonNode content = node.path("content");
            if (!content.isMissingNode()) {
                return contentText(content);
            }
            return compactJson(node);
        }
        return "";
    }

    private String collectText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        List<String> parts = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = collectText(item);
                if (!text.isBlank()) {
                    parts.add(text);
                }
            }
            return String.join("\n", parts);
        }
        if (node.isObject()) {
            Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                String text = collectText(values.next());
                if (!text.isBlank()) {
                    parts.add(text);
                }
            }
            return String.join("\n", parts);
        }
        return "";
    }

    private ChatRequest.Message message(String role, String content) {
        ChatRequest.Message message = new ChatRequest.Message();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        return switch (role.trim().toLowerCase()) {
            case "system", "developer" -> "system";
            case "assistant", "model" -> "assistant";
            default -> "user";
        };
    }

    private String firstText(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.path(fieldName);
            if (node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private String requiredText(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        if (!node.isTextual() || node.asText().isBlank()) {
            throw badRequest(fieldName + " is required");
        }
        return node.asText();
    }

    private Double doubleValue(JsonNode node) {
        if (node.isNumber()) {
            return node.asDouble();
        }
        return null;
    }

    private JsonNode firstNode(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.path(fieldName);
            if (!node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private JsonNode readObject(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw badRequest("Request body is required");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root == null || !root.isObject()) {
                throw badRequest("Request body must be a JSON object");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw badRequest("Invalid JSON request body");
        }
    }

    private String compactJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            return node.toString();
        }
    }

    private BusinessException badRequest(String message) {
        return new BusinessException("BAD_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
}
