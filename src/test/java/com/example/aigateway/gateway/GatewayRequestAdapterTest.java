package com.example.aigateway.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.aigateway.dto.request.ChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Verifies gateway request adapter behavior. */
class GatewayRequestAdapterTest {

    @Test
    void chatCompletionsShouldKeepOpenAiPayloadForPassthroughFields() {
        GatewayRequestAdapter adapter = new GatewayRequestAdapter(objectMapper());

        ChatRequest request = adapter.fromChatCompletions("""
                {
                  "providerCode": "OPENROUTER",
                  "model": "openrouter/free",
                  "messages": [
                    {
                      "role": "user",
                      "content": [
                        {"type": "text", "text": "hello"},
                        {"type": "text", "text": "world"}
                      ]
                    }
                  ],
                  "tools": [{"type": "function", "function": {"name": "lookup"}}],
                  "tool_choice": "auto",
                  "stream": false
                }
                """);

        assertEquals("OPENROUTER", request.getProviderCode());
        assertEquals("openrouter/free", request.getModel());
        assertEquals("hello\nworld", request.getMessages().getFirst().getContent());
        assertNotNull(request.getOpenAiPayload());
        assertTrue(request.getOpenAiPayload().has("tools"));
        assertTrue(request.getOpenAiPayload().has("tool_choice"));
    }

    @Test
    void responsesShouldMapInstructionsAndInputToChatMessages() {
        GatewayRequestAdapter adapter = new GatewayRequestAdapter(objectMapper());

        ChatRequest request = adapter.fromResponses("""
                {
                  "model": "gpt-4.1-mini",
                  "instructions": "Be concise.",
                  "input": [
                    {
                      "role": "user",
                      "content": [{"type": "input_text", "text": "Say hi"}]
                    }
                  ],
                  "max_output_tokens": 32
                }
                """);

        assertEquals("gpt-4.1-mini", request.getModel());
        assertEquals(2, request.getMessages().size());
        assertEquals("system", request.getMessages().get(0).getRole());
        assertEquals("Be concise.", request.getMessages().get(0).getContent());
        assertEquals("Say hi", request.getMessages().get(1).getContent());
        assertEquals(32, request.getMaxTokens());
    }

    @Test
    void anthropicMessagesShouldMapSystemAndContentBlocks() {
        GatewayRequestAdapter adapter = new GatewayRequestAdapter(objectMapper());

        ChatRequest request = adapter.fromAnthropicMessages("""
                {
                  "model": "claude-sonnet-4",
                  "system": "You are helpful.",
                  "messages": [
                    {
                      "role": "user",
                      "content": [{"type": "text", "text": "Explain Redis"}]
                    }
                  ],
                  "max_tokens": 64
                }
                """);

        assertEquals("claude-sonnet-4", request.getModel());
        assertEquals("system", request.getMessages().get(0).getRole());
        assertEquals("You are helpful.", request.getMessages().get(0).getContent());
        assertEquals("user", request.getMessages().get(1).getRole());
        assertEquals("Explain Redis", request.getMessages().get(1).getContent());
        assertEquals(64, request.getMaxTokens());
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
