package com.example.aigateway.provider.claude;

import com.example.aigateway.dto.request.ChatRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/** Anthropic payload model for claude request. */
public class ClaudeRequest {
    private String model;
    private List<Message> messages;
    private boolean stream;
    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    public static ClaudeRequest from(ChatRequest request) {
        ClaudeRequest claudeRequest = new ClaudeRequest();
        claudeRequest.setModel(request.getModel());
        claudeRequest.setStream(request.isStream());
        claudeRequest.setTemperature(request.getTemperature());
        claudeRequest.setMaxTokens(request.getMaxTokens());

        List<Message> claudeMessages = new ArrayList<>();
        for (ChatRequest.Message message : request.getMessages()) {
            if (!"system".equals(message.getRole())) {
                claudeMessages.add(Message.from(message));
            }
        }
        claudeRequest.setMessages(claudeMessages);
        return claudeRequest;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public static class Message {
        private String role;
        private String content;

        public static Message from(ChatRequest.Message chatMessage) {
            Message message = new Message();
            message.setRole(chatMessage.getRole());
            message.setContent(chatMessage.getContent());
            return message;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
