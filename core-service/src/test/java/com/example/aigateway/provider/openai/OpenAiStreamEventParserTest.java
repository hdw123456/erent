package com.example.aigateway.provider.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.aigateway.provider.ProviderStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Verifies open ai stream event parser behavior. */
class OpenAiStreamEventParserTest {
    private final OpenAiStreamEventParser parser = new OpenAiStreamEventParser(new ObjectMapper());

    @Test
    void parsesTextFinishReasonAndUsageChunks() {
        ProviderStreamEvent text = parser.parse("""
                {"id":"chat_1","model":"gpt-test","choices":[{"delta":{"role":"assistant","content":"Hi"},"finish_reason":null}]}
                """);
        ProviderStreamEvent usage = parser.parse("""
                {"id":"chat_1","model":"gpt-test","choices":[],"usage":{"prompt_tokens":4,"completion_tokens":2,"total_tokens":6}}
                """);

        assertEquals("assistant", text.role());
        assertEquals("Hi", text.textDelta());
        assertEquals(4, usage.usage().getPromptTokens());
        assertEquals(2, usage.usage().getCompletionTokens());
    }

    @Test
    void recognizesDoneMarker() {
        assertTrue(parser.parse("[DONE]").done());
    }

    @Test
    void preservesWhitespaceOnlyTextDelta() {
        ProviderStreamEvent event = parser.parse("""
                {"choices":[{"delta":{"content":" "}}]}
                """);

        assertEquals(" ", event.textDelta());
    }
}
