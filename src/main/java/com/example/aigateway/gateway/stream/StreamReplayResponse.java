package com.example.aigateway.gateway.stream;

import com.example.aigateway.dto.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;

/** Cached terminal response and exact SSE frames used for idempotent replay. */
public class StreamReplayResponse {
    private ChatResponse response;
    private List<GatewayStreamFrame> frames = new ArrayList<>();

    public StreamReplayResponse() {
    }

    public StreamReplayResponse(ChatResponse response, List<GatewayStreamFrame> frames) {
        this.response = response;
        this.frames = new ArrayList<>(frames);
    }

    public ChatResponse getResponse() {
        return response;
    }

    public void setResponse(ChatResponse response) {
        this.response = response;
    }

    public List<GatewayStreamFrame> getFrames() {
        return frames;
    }

    public void setFrames(List<GatewayStreamFrame> frames) {
        this.frames = frames == null ? new ArrayList<>() : new ArrayList<>(frames);
    }
}
