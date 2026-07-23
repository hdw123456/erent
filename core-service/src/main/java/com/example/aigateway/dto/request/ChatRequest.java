package com.example.aigateway.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Validated request data for chat operations. */
public class ChatRequest {
    @Size(max = 32)
    @JsonAlias({"provider", "provider_code"})
    @JsonProperty("providerCode")
    private String providerCode;

    @NotBlank
    @Size(max = 256)
    private String model;

    @Valid
    @NotEmpty
    @Size(max = 100)
    private List<Message> messages;

    private boolean stream;

    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private Double temperature;

    @Positive
    @JsonAlias("max_tokens")
    private Integer maxTokens;

    @JsonIgnore
    private ObjectNode openAiPayload;

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
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

    public ObjectNode getOpenAiPayload() {
        return openAiPayload;
    }

    public void setOpenAiPayload(ObjectNode openAiPayload) {
        this.openAiPayload = openAiPayload;
    }

    public static class Message {
        @NotBlank
        @Pattern(regexp = "system|user|assistant", message = "role must be system, user, or assistant")
        private String role;

        @NotBlank
        @Size(max = 20000)
        private String content;

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
