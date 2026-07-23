package com.example.aigateway.gateway.stream;

import java.io.IOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Writes gateway frames to a Spring MVC {@link SseEmitter}. */
public class SseGatewayStreamSink implements GatewayStreamSink {
    private final SseEmitter emitter;

    public SseGatewayStreamSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(GatewayStreamFrame frame) throws IOException {
        SseEmitter.SseEventBuilder event = SseEmitter.event();
        if (frame.event() != null && !frame.event().isBlank()) {
            event.name(frame.event());
        }
        emitter.send(event.data(frame.data()));
    }

    @Override
    public void complete() {
        emitter.complete();
    }

    @Override
    public void completeWithError(Throwable throwable) {
        emitter.completeWithError(throwable);
    }
}
