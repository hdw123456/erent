package com.example.aigateway.messaging.consumer;

import com.example.aigateway.messaging.RabbitTopology;
import com.example.aigateway.messaging.event.UsageRecordedEvent;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Minimal usage-event consumer; aggregation persistence is intentionally deferred. */
@Component
public class UsageStatisticsConsumer {
    private static final Logger logger = LoggerFactory.getLogger(UsageStatisticsConsumer.class);

    @RabbitListener(queues = RabbitTopology.USAGE_STATISTICS_QUEUE)
    public void consume(
            UsageRecordedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            logger.info(
                    "Received usage event: eventId={}, requestId={}, totalTokens={}, cost={}",
                    event.eventId(),
                    event.requestId(),
                    event.totalTokens(),
                    event.costAmount());

            // The skeleton only proves routing and acknowledgement. A later stage adds idempotent aggregation.
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            logger.warn("Failed to consume usage event: eventId={}", event.eventId(), exception);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
