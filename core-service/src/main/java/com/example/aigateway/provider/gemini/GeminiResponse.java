package com.example.aigateway.provider.gemini;

import com.example.aigateway.dto.response.ChatResponse;
import java.util.List;

/** Gemini payload model for gemini response. */
public class GeminiResponse {
    private List<Candidate> candidates;

    public ChatResponse toChatResponse(String model) {
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setModel(model);

        if (candidates != null && !candidates.isEmpty()) {
            Candidate candidate = candidates.getFirst();
            chatResponse.setFinishReason(candidate.getFinishReason());
            if (candidate.getContent() != null
                    && candidate.getContent().getParts() != null
                    && !candidate.getContent().getParts().isEmpty()) {
                ChatResponse.Message message = new ChatResponse.Message();
                message.setRole("assistant");
                message.setContent(candidate.getContent().getParts().getFirst().getText());
                chatResponse.setMessage(message);
            }
        }
        return chatResponse;
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<Candidate> candidates) {
        this.candidates = candidates;
    }

    public static class Candidate {
        private Content content;
        private String finishReason;

        public Content getContent() {
            return content;
        }

        public void setContent(Content content) {
            this.content = content;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }

    public static class Content {
        private List<Part> parts;

        public List<Part> getParts() {
            return parts;
        }

        public void setParts(List<Part> parts) {
            this.parts = parts;
        }
    }

    public static class Part {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
