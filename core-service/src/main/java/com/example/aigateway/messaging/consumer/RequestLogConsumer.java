package com.example.aigateway.messaging.consumer;

import com.example.aigateway.messaging.RabbitTopology;
import com.example.aigateway.messaging.event.RequestCompletedEvent;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Minimal request-event consumer; persistence is intentionally deferred. */
@Component
public class RequestLogConsumer {
    private static final Logger logger = LoggerFactory.getLogger(RequestLogConsumer.class);

    @RabbitListener(queues = RabbitTopology.REQUEST_LOG_QUEUE)
    public void consume(
            RequestCompletedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            logger.info(
                    "Received request event: eventId={}, requestId={}, status={}, latencyMs={}",
                    event.eventId(),
                    event.requestId(),
                    event.statusCode(),
                    event.latencyMs());

            // The skeleton only proves routing and acknowledgement. A later stage adds idempotent persistence.
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            logger.warn("Failed to consume request event: eventId={}", event.eventId(), exception);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
