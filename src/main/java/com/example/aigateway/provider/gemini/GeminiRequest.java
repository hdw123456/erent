package com.example.aigateway.provider.gemini;

import com.example.aigateway.dto.request.ChatRequest;
import java.util.ArrayList;
import java.util.List;

/** Gemini payload model for gemini request. */
public class GeminiRequest {
    private List<Content> contents;

    public static GeminiRequest from(ChatRequest request) {
        GeminiRequest geminiRequest = new GeminiRequest();
        List<Content> contents = new ArrayList<>();
        for (ChatRequest.Message message : request.getMessages()) {
            if (!"system".equals(message.getRole())) {
                contents.add(Content.from(message));
            }
        }
        geminiRequest.setContents(contents);
        return geminiRequest;
    }

    public List<Content> getContents() {
        return contents;
    }

    public void setContents(List<Content> contents) {
        this.contents = contents;
    }

    public static class Content {
        private String role;
        private List<Part> parts;

        public static Content from(ChatRequest.Message chatMessage) {
            Content content = new Content();
            content.setRole("assistant".equals(chatMessage.getRole()) ? "model" : "user");
            content.setParts(List.of(new Part(chatMessage.getContent())));
            return content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public List<Part> getParts() {
            return parts;
        }

        public void setParts(List<Part> parts) {
            this.parts = parts;
        }
    }

    public static class Part {
        private String text;

        public Part() {
        }

        public Part(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
