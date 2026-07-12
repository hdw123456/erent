package com.example.aigateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** Verifies model call service fingerprint behavior. */
class ModelCallServiceFingerprintTest {

    @Test
    void canonicalizePayloadShouldIgnoreJsonObjectFieldOrderAndWhitespace() throws Exception {
        ModelCallService service = service();
        Method canonicalize = ModelCallService.class.getDeclaredMethod("canonicalizePayload", String.class);
        canonicalize.setAccessible(true);

        String first = (String) canonicalize.invoke(service, "{\"model\":\"gpt\",\"messages\":[]}");
        String second = (String) canonicalize.invoke(service, """
                {
                  "messages": [],
                  "model": "gpt"
                }
                """);

        assertEquals(first, second);
    }

    @Test
    void fingerprintShouldIncludeRoute() throws Exception {
        ModelCallService service = service();
        Method canonicalize = ModelCallService.class.getDeclaredMethod("canonicalizePayload", String.class);
        Method fingerprint = ModelCallService.class.getDeclaredMethod(
                "buildRequestFingerprint", String.class, String.class, String.class, String.class);
        canonicalize.setAccessible(true);
        fingerprint.setAccessible(true);

        String payload = (String) canonicalize.invoke(service, "{\"model\":\"gpt\",\"messages\":[]}");
        String chatFingerprint = (String) fingerprint.invoke(service, "POST", "chat_completions", "api_key:1", payload);
        String messagesFingerprint = (String) fingerprint.invoke(service, "POST", "messages", "api_key:1", payload);

        assertNotEquals(chatFingerprint, messagesFingerprint);
    }

    private ModelCallService service() {
        return new ModelCallService(null, null, null, null, null, null, null, null, new ObjectMapper());
    }
}
