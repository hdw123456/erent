package com.example.aigateway.gateway.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.aigateway.dto.response.ChatResponse;
import com.example.aigateway.provider.ProviderStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies gateway stream response adapter behavior. */
class GatewayStreamResponseAdapterTest {
    private final GatewayStreamResponseAdapter adapter = new GatewayStreamResponseAdapter(new ObjectMapper());

    @Test
    void openAiPassthroughDefersDoneUntilFinish() {
        GatewayStreamResponseAdapter.Session session = adapter.open(
                GatewayStreamProtocol.OPENAI_CHAT_COMPLETIONS, "req_1", "gpt-test");
        ProviderStreamEvent chunk = new ProviderStreamEvent(
                "{\"choices\":[]}", null, null, null, null, null, null, false);

        assertEquals("{\"choices\":[]}", session.accept(chunk).getFirst().data());
        assertTrue(session.accept(ProviderStreamEvent.done("[DONE]")).isEmpty());
        assertEquals("[DONE]", session.finish(response()).getFirst().data());
    }

    @Test
    void anthropicStreamHasExpectedLifecycle() {
        GatewayStreamResponseAdapter.Session session = adapter.open(
                GatewayStreamProtocol.ANTHROPIC_MESSAGES, "req_1", "gpt-test");
        List<GatewayStreamFrame> initial = session.accept(textEvent("Hello"));
        List<GatewayStreamFrame> terminal = session.finish(response());

        assertEquals(List.of("message_start", "content_block_start", "content_block_delta"),
                initial.stream().map(GatewayStreamFrame::event).toList());
        assertEquals(List.of("content_block_stop", "message_delta", "message_stop"),
                terminal.stream().map(GatewayStreamFrame::event).toList());
    }

    @Test
    void responsesStreamHasExpectedLifecycle() {
        GatewayStreamResponseAdapter.Session session = adapter.open(
                GatewayStreamProtocol.OPENAI_RESPONSES, "req_1", "gpt-test");
        List<GatewayStreamFrame> initial = session.accept(textEvent("Hello"));
        List<GatewayStreamFrame> terminal = session.finish(response());

        assertEquals("response.created", initial.get(0).event());
        assertEquals("response.output_text.delta", initial.get(4).event());
        assertEquals("response.output_text.done", terminal.get(0).event());
        assertEquals("response.completed", terminal.get(3).event());
    }

    private ProviderStreamEvent textEvent(String text) {
        return new ProviderStreamEvent(null, "upstream_1", "gpt-test", "assistant", text, null, null, false);
    }

    private ChatResponse response() {
        ChatResponse response = new ChatResponse();
        response.setRequestId("req_1");
        response.setId("upstream_1");
        response.setModel("gpt-test");
        response.setFinishReason("stop");
        ChatResponse.Message message = new ChatResponse.Message();
        message.setRole("assistant");
        message.setContent("Hello");
        response.setMessage(message);
        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(3);
        usage.setCompletionTokens(2);
        usage.setTotalTokens(5);
        response.setUsage(usage);
        return response;
    }
}
