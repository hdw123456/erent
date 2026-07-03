package com.example.aigateway.provider.openai;

import com.example.aigateway.dto.response.ChatResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OpenaiResponse {
    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    public ChatResponse toChatResponse() {
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setId(id);
        chatResponse.setModel(model);

        if (choices != null && !choices.isEmpty()) {
            Choice choice = choices.getFirst();
            chatResponse.setFinishReason(choice.getFinishReason());
            if (choice.getMessage() != null) {
                ChatResponse.Message message = new ChatResponse.Message();
                message.setRole(choice.getMessage().getRole());
                message.setContent(choice.getMessage().getContent());
                chatResponse.setMessage(message);
            }
        }

        if (usage != null) {
            ChatResponse.Usage chatUsage = new ChatResponse.Usage();
            chatUsage.setPromptTokens(usage.getPromptTokens());
            chatUsage.setCompletionTokens(usage.getCompletionTokens());
            chatUsage.setTotalTokens(usage.getTotalTokens());
            chatResponse.setUsage(chatUsage);
        }
        return chatResponse;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public static class Choice {
        private Integer index;
        private Message message;

        @JsonProperty("finish_reason")
        private String finishReason;

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }

    public static class Message {
        private String role;
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

    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }
    }
}
