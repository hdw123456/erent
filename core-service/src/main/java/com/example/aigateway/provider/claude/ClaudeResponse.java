package com.example.aigateway.provider.claude;

import com.example.aigateway.dto.response.ChatResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Anthropic payload model for claude response. */
public class ClaudeResponse {
    private String id;
    private String model;
    private List<Content> content;

    @JsonProperty("stop_reason")
    private String stopReason;

    public ChatResponse toChatResponse() {
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setId(id);
        chatResponse.setModel(model);
        chatResponse.setFinishReason(stopReason);

        if (content != null && !content.isEmpty()) {
            ChatResponse.Message message = new ChatResponse.Message();
            message.setRole("assistant");
            message.setContent(content.getFirst().getText());
            chatResponse.setMessage(message);
        }
        return chatResponse;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Content> getContent() {
        return content;
    }

    public void setContent(List<Content> content) {
        this.content = content;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public static class Content {
        private String type;
        private String text;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
