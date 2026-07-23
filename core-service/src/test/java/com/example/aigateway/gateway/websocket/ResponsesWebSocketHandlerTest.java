package com.example.aigateway.gateway.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.gateway.GatewayRequestAdapter;
import com.example.aigateway.gateway.stream.GatewayStreamFrame;
import com.example.aigateway.gateway.stream.GatewayStreamProtocol;
import com.example.aigateway.gateway.stream.GatewayStreamResponseAdapter;
import com.example.aigateway.gateway.stream.GatewayStreamSink;
import com.example.aigateway.security.ApiKeyPrincipal;
import com.example.aigateway.service.ModelCallService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/** Verifies responses web socket handler behavior. */
class ResponsesWebSocketHandlerTest {

    @Test
    void responseCreateUsesResponsesStreamSink() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.requestAdapter.fromResponses(anyString())).thenReturn(request("hello"));
        when(fixture.modelCallService.streamToSink(
                any(), any(), any(), anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    GatewayStreamSink sink = invocation.getArgument(6);
                    sink.send(new GatewayStreamFrame("response.completed", completed("resp_1", "answer")));
                    sink.complete();
                    return (Runnable) () -> {
                    };
                });

        fixture.handler.afterConnectionEstablished(fixture.session);
        fixture.handler.handleMessage(
                fixture.session,
                new TextMessage("{\"type\":\"response.create\",\"model\":\"gpt-test\",\"input\":\"hello\"}"));

        verify(fixture.modelCallService).streamToSink(
                any(ChatRequest.class),
                any(ApiKeyPrincipal.class),
                any(),
                org.mockito.ArgumentMatchers.eq("responses_websocket"),
                anyString(),
                org.mockito.ArgumentMatchers.eq(GatewayStreamProtocol.OPENAI_RESPONSES),
                any(GatewayStreamSink.class));
        assertEquals(1, fixture.sentMessages.size());
    }

    @Test
    void previousResponseContinuesConnectionLocalTextHistory() throws Exception {
        Fixture fixture = new Fixture();
        ChatRequest first = request("hello");
        ChatRequest second = request("next");
        when(fixture.requestAdapter.fromResponses(anyString())).thenReturn(first, second);
        when(fixture.modelCallService.streamToSink(
                any(), any(), any(), anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    GatewayStreamSink sink = invocation.getArgument(6);
                    sink.send(new GatewayStreamFrame("response.completed", completed("resp_1", "answer")));
                    sink.complete();
                    return (Runnable) () -> {
                    };
                })
                .thenReturn(() -> {
                });

        fixture.handler.afterConnectionEstablished(fixture.session);
        fixture.handler.handleMessage(
                fixture.session,
                new TextMessage("{\"type\":\"response.create\",\"model\":\"gpt-test\",\"input\":\"hello\"}"));
        fixture.handler.handleMessage(
                fixture.session,
                new TextMessage("{\"type\":\"response.create\",\"model\":\"gpt-test\",\"previous_response_id\":\"resp_1\",\"input\":\"next\"}"));

        ArgumentCaptor<ChatRequest> requests = ArgumentCaptor.forClass(ChatRequest.class);
        verify(fixture.modelCallService, org.mockito.Mockito.times(2)).streamToSink(
                requests.capture(), any(), any(), anyString(), anyString(), any(), any());
        ChatRequest continued = requests.getAllValues().get(1);
        assertEquals(3, continued.getMessages().size());
        assertEquals("answer", continued.getMessages().get(1).getContent());
        assertEquals("next", continued.getMessages().get(2).getContent());
    }

    @Test
    void rejectsAnotherResponseWhileOneIsInFlight() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.requestAdapter.fromResponses(anyString())).thenReturn(request("hello"));
        when(fixture.modelCallService.streamToSink(
                any(), any(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(() -> {
                });

        fixture.handler.afterConnectionEstablished(fixture.session);
        fixture.handler.handleMessage(
                fixture.session,
                new TextMessage("{\"type\":\"response.create\",\"model\":\"gpt-test\",\"input\":\"hello\"}"));
        fixture.handler.handleMessage(
                fixture.session,
                new TextMessage("{\"type\":\"response.create\",\"model\":\"gpt-test\",\"input\":\"again\"}"));

        verify(fixture.modelCallService, times(1)).streamToSink(
                any(), any(), any(), anyString(), anyString(), any(), any());
        assertTrue(fixture.sentMessages.stream()
                .anyMatch(payload -> payload.contains("WEBSOCKET_RESPONSE_IN_PROGRESS")));
    }

    @Test
    void failedTurnInvalidatesConnectionLocalPreviousResponse() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.requestAdapter.fromResponses(anyString()))
                .thenReturn(request("hello"), request("fails"), request("retry"));
        when(fixture.modelCallService.streamToSink(
                any(), any(), any(), anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    GatewayStreamSink sink = invocation.getArgument(6);
                    sink.send(new GatewayStreamFrame("response.completed", completed("resp_1", "answer")));
                    sink.complete();
                    return (Runnable) () -> {
                    };
                })
                .thenAnswer(invocation -> {
                    GatewayStreamSink sink = invocation.getArgument(6);
                    sink.completeWithError(new IOException("upstream failed"));
                    return (Runnable) () -> {
                    };
                });

        fixture.handler.afterConnectionEstablished(fixture.session);
        fixture.handler.handleMessage(
                fixture.session,
                new TextMessage("{\"type\":\"response.create\",\"model\":\"gpt-test\",\"input\":\"hello\"}"));
        fixture.handler.handleMessage(
                fixture.session,
                new TextMessage("{\"type\":\"response.create\",\"model\":\"gpt-test\",\"previous_response_id\":\"resp_1\",\"input\":\"fails\"}"));
        fixture.handler.handleMessage(
                fixture.session,
                new TextMessage("{\"type\":\"response.create\",\"model\":\"gpt-test\",\"previous_response_id\":\"resp_1\",\"input\":\"retry\"}"));

        verify(fixture.modelCallService, times(2)).streamToSink(
                any(), any(), any(), anyString(), anyString(), any(), any());
        assertTrue(fixture.sentMessages.stream()
                .anyMatch(payload -> payload.contains("previous_response_not_found")));
    }

    @Test
    void closingConnectionCancelsInFlightProviderStream() throws Exception {
        Fixture fixture = new Fixture();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        when(fixture.requestAdapter.fromResponses(anyString())).thenReturn(request("hello"));
        when(fixture.modelCallService.streamToSink(
                any(), any(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(() -> cancelled.set(true));

        fixture.handler.afterConnectionEstablished(fixture.session);
        fixture.handler.handleMessage(
                fixture.session,
                new TextMessage("{\"type\":\"response.create\",\"model\":\"gpt-test\",\"input\":\"hello\"}"));
        fixture.handler.afterConnectionClosed(fixture.session, CloseStatus.NORMAL);

        assertTrue(cancelled.get());
    }

    private static ChatRequest request(String content) {
        ChatRequest request = new ChatRequest();
        request.setModel("gpt-test");
        ChatRequest.Message message = new ChatRequest.Message();
        message.setRole("user");
        message.setContent(content);
        request.setMessages(new ArrayList<>(List.of(message)));
        return request;
    }

    private static String completed(String responseId, String text) {
        return """
                {"type":"response.completed","response":{"id":"%s","output":[{"content":[{"text":"%s"}]}]}}
                """.formatted(responseId, text).trim();
    }

    private static final class Fixture {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final GatewayRequestAdapter requestAdapter = mock(GatewayRequestAdapter.class);
        private final ModelCallService modelCallService = mock(ModelCallService.class);
        private final GatewayStreamResponseAdapter responseAdapter = new GatewayStreamResponseAdapter(objectMapper);
        private final ResponsesWebSocketHandler handler = new ResponsesWebSocketHandler(
                objectMapper, requestAdapter, responseAdapter, modelCallService);
        private final WebSocketSession session = mock(WebSocketSession.class);
        private final List<String> sentMessages = new ArrayList<>();

        private Fixture() throws Exception {
            ApiKeyPrincipal apiKeyPrincipal = new ApiKeyPrincipal(10L, 100L, "ak_test");
            when(session.getId()).thenReturn("ws_1");
            when(session.getPrincipal()).thenReturn(
                    new UsernamePasswordAuthenticationToken(apiKeyPrincipal, null, List.of()));
            when(session.isOpen()).thenReturn(true);
            org.mockito.Mockito.doAnswer(invocation -> {
                WebSocketMessage<?> message = invocation.getArgument(0);
                sentMessages.add(message.getPayload().toString());
                return null;
            }).when(session).sendMessage(any(WebSocketMessage.class));
        }
    }
}
