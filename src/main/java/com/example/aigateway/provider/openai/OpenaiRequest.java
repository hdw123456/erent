package com.example.aigateway.provider.openai;

import com.example.aigateway.dto.request.ChatRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/** OpenAI-compatible chat-completions request payload. */
public class OpenaiRequest {
    private String model;
    private List<Message> messages;
    private boolean stream;
    private Double temperature;

    @JsonProperty("stream_options")
    private StreamOptions streamOptions;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    public static OpenaiRequest from(ChatRequest request) {
        OpenaiRequest openaiRequest = new OpenaiRequest();
        openaiRequest.setModel(request.getModel());
        openaiRequest.setStream(request.isStream());
        openaiRequest.setTemperature(request.getTemperature());
        openaiRequest.setMaxTokens(request.getMaxTokens());

        List<Message> openaiMessages = new ArrayList<>();
        for (ChatRequest.Message message : request.getMessages()) {
            openaiMessages.add(Message.from(message));
        }
        openaiRequest.setMessages(openaiMessages);
        return openaiRequest;
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

    public StreamOptions getStreamOptions() {
        return streamOptions;
    }

    public void setStreamOptions(StreamOptions streamOptions) {
        this.streamOptions = streamOptions;
    }

    /** Requests the terminal usage chunk for a streamed chat completion. */
    public static class StreamOptions {
        @JsonProperty("include_usage")
        private boolean includeUsage;

        public static StreamOptions includeUsage() {
            StreamOptions options = new StreamOptions();
            options.setIncludeUsage(true);
            return options;
        }

        public boolean isIncludeUsage() {
            return includeUsage;
        }

        public void setIncludeUsage(boolean includeUsage) {
            this.includeUsage = includeUsage;
        }
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
