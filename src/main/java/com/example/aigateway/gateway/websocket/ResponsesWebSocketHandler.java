package com.example.aigateway.gateway.websocket;

import com.example.aigateway.dto.request.ChatRequest;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.gateway.GatewayRequestAdapter;
import com.example.aigateway.gateway.stream.GatewayStreamFrame;
import com.example.aigateway.gateway.stream.GatewayStreamProtocol;
import com.example.aigateway.gateway.stream.GatewayStreamResponseAdapter;
import com.example.aigateway.gateway.stream.GatewayStreamSink;
import com.example.aigateway.security.ApiKeyPrincipal;
import com.example.aigateway.service.ModelCallService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Handles sequential Responses API turns over one authenticated WebSocket. */
@Component
public class ResponsesWebSocketHandler extends TextWebSocketHandler {
    private static final String RESPONSE_CREATE = "response.create";
    private static final Runnable NOOP = () -> {
    };

    private final ObjectMapper objectMapper;
    private final GatewayRequestAdapter requestAdapter;
    private final GatewayStreamResponseAdapter responseAdapter;
    private final ModelCallService modelCallService;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public ResponsesWebSocketHandler(
            ObjectMapper objectMapper,
            GatewayRequestAdapter requestAdapter,
            GatewayStreamResponseAdapter responseAdapter,
            ModelCallService modelCallService) {
        this.objectMapper = objectMapper;
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
        this.modelCallService = modelCallService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new SessionState());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SessionState state = sessions.computeIfAbsent(session.getId(), ignored -> new SessionState());
        try {
            JsonNode event = readEvent(message.getPayload());
            if (!RESPONSE_CREATE.equals(event.path("type").asText())) {
                throw badRequest("Expected a response.create event");
            }
            if (!state.inFlight.compareAndSet(false, true)) {
                throw new BusinessException(
                        "WEBSOCKET_RESPONSE_IN_PROGRESS",
                        "Only one response may run at a time on a WebSocket connection",
                        HttpStatus.CONFLICT);
            }

            ApiKeyPrincipal principal = requireApiKeyPrincipal(session.getPrincipal());
            ChatRequest request = requestAdapter.fromResponses(message.getPayload());
            request.setStream(true);
            List<ChatRequest.Message> pendingHistory = state.prepareHistory(
                    request, textOrNull(event.path("previous_response_id")));
            String idempotencyKey = textOrNull(event.path("idempotency_key"));
            WebSocketStreamSink sink = new WebSocketStreamSink(session, state, pendingHistory);

            Runnable cancellation = modelCallService.streamToSink(
                    request,
                    principal,
                    idempotencyKey,
                    "responses_websocket",
                    message.getPayload(),
                    GatewayStreamProtocol.OPENAI_RESPONSES,
                    sink);
            state.registerCancellation(cancellation);
        } catch (BusinessException exception) {
            state.inFlight.set(false);
            sendError(session, exception);
        } catch (Throwable throwable) {
            state.inFlight.set(false);
            sendError(session, new BusinessException(
                    "WEBSOCKET_REQUEST_FAILED",
                    "Failed to process WebSocket request",
                    HttpStatus.BAD_REQUEST));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionState state = sessions.remove(session.getId());
        if (state != null) {
            state.cancelTurn();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        SessionState state = sessions.get(session.getId());
        if (state != null) {
            state.cancelTurn();
        }
    }

    private JsonNode readEvent(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            if (event == null || !event.isObject()) {
                throw badRequest("WebSocket event must be a JSON object");
            }
            return event;
        } catch (JsonProcessingException exception) {
            throw badRequest("Invalid WebSocket JSON event");
        }
    }

    private ApiKeyPrincipal requireApiKeyPrincipal(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof ApiKeyPrincipal apiKeyPrincipal) {
            return apiKeyPrincipal;
        }
        if (principal instanceof ApiKeyPrincipal apiKeyPrincipal) {
            return apiKeyPrincipal;
        }
        throw new BusinessException("UNAUTHORIZED", "API key is required", HttpStatus.UNAUTHORIZED);
    }

    private void sendError(WebSocketSession session, BusinessException exception) {
        for (GatewayStreamFrame frame : responseAdapter.error(
                GatewayStreamProtocol.OPENAI_RESPONSES, exception, session.getId())) {
            try {
                sendText(session, frame.data());
            } catch (IOException ignored) {
                return;
            }
        }
    }

    private void sendText(WebSocketSession session, String data) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(data));
            }
        }
    }

    private String textOrNull(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException("BAD_REQUEST", message, HttpStatus.BAD_REQUEST);
    }

    private final class WebSocketStreamSink implements GatewayStreamSink {
        private final WebSocketSession session;
        private final SessionState state;
        private final List<ChatRequest.Message> pendingHistory;
        private boolean failed;
        private String responseId;
        private String responseText;

        private WebSocketStreamSink(
                WebSocketSession session,
                SessionState state,
                List<ChatRequest.Message> pendingHistory) {
            this.session = session;
            this.state = state;
            this.pendingHistory = pendingHistory;
        }

        @Override
        public void send(GatewayStreamFrame frame) throws IOException {
            inspect(frame.data());
            sendText(session, frame.data());
        }

        @Override
        public void complete() {
            if (!failed && responseId != null) {
                state.completeTurn(pendingHistory, responseId, responseText);
            } else {
                state.failTurn();
            }
        }

        @Override
        public void completeWithError(Throwable throwable) {
            failed = true;
            state.failTurn();
        }

        private void inspect(String data) {
            try {
                JsonNode event = objectMapper.readTree(data);
                if ("error".equals(event.path("type").asText())) {
                    failed = true;
                    return;
                }
                if (!"response.completed".equals(event.path("type").asText())) {
                    return;
                }
                JsonNode response = event.path("response");
                responseId = textOrNull(response.path("id"));
                responseText = response.path("output").path(0).path("content").path(0).path("text").asText("");
            } catch (JsonProcessingException ignored) {
                // Non-JSON data is not expected for the Responses protocol.
            }
        }
    }

    private static final class SessionState {
        private final AtomicBoolean inFlight = new AtomicBoolean(false);
        private final AtomicReference<Runnable> cancellation = new AtomicReference<>(NOOP);
        private List<ChatRequest.Message> history = new ArrayList<>();
        private String lastResponseId;

        private synchronized List<ChatRequest.Message> prepareHistory(
                ChatRequest request,
                String previousResponseId) {
            List<ChatRequest.Message> pending = new ArrayList<>();
            if (previousResponseId != null) {
                if (!previousResponseId.equals(lastResponseId)) {
                    throw new BusinessException(
                            "previous_response_not_found",
                            "Previous response is not available on this connection",
                            HttpStatus.BAD_REQUEST);
                }
                pending.addAll(history);
            }
            pending.addAll(request.getMessages());
            request.setMessages(pending);
            return new ArrayList<>(pending);
        }

        private synchronized void completeTurn(
                List<ChatRequest.Message> pendingHistory,
                String responseId,
                String responseText) {
            history = new ArrayList<>(pendingHistory);
            if (responseText != null && !responseText.isBlank()) {
                ChatRequest.Message assistant = new ChatRequest.Message();
                assistant.setRole("assistant");
                assistant.setContent(responseText);
                history.add(assistant);
            }
            lastResponseId = responseId;
            finishTurn();
        }

        private synchronized void failTurn() {
            history = new ArrayList<>();
            lastResponseId = null;
            finishTurn();
        }

        private void registerCancellation(Runnable callback) {
            Runnable safeCallback = callback == null ? NOOP : callback;
            if (!inFlight.get()) {
                return;
            }
            cancellation.set(safeCallback);
            if (!inFlight.get() && cancellation.compareAndSet(safeCallback, NOOP)) {
                safeCallback.run();
            }
        }

        private void cancelTurn() {
            inFlight.set(false);
            cancellation.getAndSet(NOOP).run();
        }

        private void finishTurn() {
            inFlight.set(false);
            cancellation.set(NOOP);
        }
    }
}
