package com.example.aigateway.gateway.stream;

import java.io.IOException;

/** Transport boundary shared by HTTP SSE and persistent WebSocket streams. */
public interface GatewayStreamSink {
    void send(GatewayStreamFrame frame) throws IOException;

    void complete();

    void completeWithError(Throwable throwable);
}
