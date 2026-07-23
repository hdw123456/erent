package com.example.aigateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.provider.ProviderStreamEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies stream response accumulator behavior. */
class StreamResponseAccumulatorTest {

    @Test
    void usesProviderUsageWhenComplete() {
        StreamResponseAccumulator accumulator = new StreamResponseAccumulator(request(), "req_1", "gpt-test");
        accumulator.accept(event("Hello", null));
        accumulator.accept(event(null, usage(10, 3, 13)));

        StreamResponseAccumulator.Result result = accumulator.result();

        assertEquals(StreamResponseAccumulator.USAGE_SOURCE_PROVIDER, result.usageSource());
        assertEquals("Hello", result.response().getMessage().getContent());
        assertEquals(13, result.response().getUsage().getTotalTokens());
    }

    @Test
    void marksFallbackUsageAsEstimated() {
        StreamResponseAccumulator accumulator = new StreamResponseAccumulator(request(), "req_1", "gpt-test");
        accumulator.accept(event("Hello", null));

        StreamResponseAccumulator.Result result = accumulator.result();

        assertEquals(StreamResponseAccumulator.USAGE_SOURCE_ESTIMATED, result.usageSource());
        assertTrue(result.response().getUsage().getPromptTokens() > 0);
        assertTrue(result.response().getUsage().getCompletionTokens() > 0);
    }

    private ChatRequest request() {
        ChatRequest request = new ChatRequest();
        request.setModel("gpt-test");
        ChatRequest.Message message = new ChatRequest.Message();
        message.setRole("user");
        message.setContent("Say hello");
        request.setMessages(List.of(message));
        return request;
    }

    private ProviderStreamEvent event(String text, ChatResponse.Usage usage) {
        return new ProviderStreamEvent(null, "chat_1", "gpt-test", "assistant", text, null, usage, false);
    }

    private ChatResponse.Usage usage(int input, int output, int total) {
        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(input);
        usage.setCompletionTokens(output);
        usage.setTotalTokens(total);
        return usage;
    }
}
