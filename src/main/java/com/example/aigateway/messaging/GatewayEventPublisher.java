package com.example.aigateway.messaging;

import com.example.aigateway.messaging.event.RequestCompletedEvent;
import com.example.aigateway.messaging.event.UsageRecordedEvent;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Publishes versioned gateway events to the shared topic exchange. */
@Component
public class GatewayEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public GatewayEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishRequestCompleted(RequestCompletedEvent event) {
        send(RabbitTopology.REQUEST_COMPLETED, event);
    }

    public void publishUsageRecorded(UsageRecordedEvent event) {
        send(RabbitTopology.USAGE_RECORDED, event);
    }

    private void send(String routingKey, Object event) {
        rabbitTemplate.convertAndSend(
                RabbitTopology.EVENTS_EXCHANGE,
                routingKey,
                event,
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                });
    }
}
