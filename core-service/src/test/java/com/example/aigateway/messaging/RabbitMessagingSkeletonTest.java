package com.example.aigateway.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.aigateway.messaging.consumer.RequestLogConsumer;
import com.example.aigateway.messaging.consumer.UsageStatisticsConsumer;
import com.example.aigateway.messaging.event.RequestCompletedEvent;
import com.example.aigateway.messaging.event.UsageRecordedEvent;
import com.rabbitmq.client.Channel;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Verifies the RabbitMQ skeleton without requiring a running broker. */
class RabbitMessagingSkeletonTest {

    @Test
    void topologyShouldDeclareDurableTopicRoutes() {
        RabbitTopology topology = new RabbitTopology();
        TopicExchange exchange = topology.gatewayEventsExchange();
        Queue requestQueue = topology.requestLogQueue();
        Queue usageQueue = topology.usageStatisticsQueue();
        Queue notificationQueue = topology.notificationQueue();

        Binding requestBinding = topology.requestLogBinding(requestQueue, exchange);
        Binding usageBinding = topology.usageStatisticsBinding(usageQueue, exchange);
        Binding notificationBinding = topology.notificationBinding(notificationQueue, exchange);

        assertEquals(RabbitTopology.EVENTS_EXCHANGE, exchange.getName());
        assertTrue(exchange.isDurable());
        assertTrue(requestQueue.isDurable());
        assertTrue(usageQueue.isDurable());
        assertTrue(notificationQueue.isDurable());
        assertEquals("request.#", requestBinding.getRoutingKey());
        assertEquals("usage.#", usageBinding.getRoutingKey());
        assertEquals("notification.#", notificationBinding.getRoutingKey());
    }

    @Test
    void publisherShouldUseExpectedRouteAndPersistentDelivery() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        GatewayEventPublisher publisher = new GatewayEventPublisher(rabbitTemplate);
        RequestCompletedEvent event = requestEvent();

        publisher.publishRequestCompleted(event);

        ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitTopology.EVENTS_EXCHANGE),
                eq(RabbitTopology.REQUEST_COMPLETED),
                eq(event),
                processorCaptor.capture());

        Message message = new Message(new byte[0], new MessageProperties());
        processorCaptor.getValue().postProcessMessage(message);
        assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
    }

    @Test
    void consumersShouldAcknowledgeSuccessfullyHandledEvents() throws Exception {
        Channel requestChannel = mock(Channel.class);
        Channel usageChannel = mock(Channel.class);

        new RequestLogConsumer().consume(requestEvent(), requestChannel, 11L);
        new UsageStatisticsConsumer().consume(usageEvent(), usageChannel, 12L);

        verify(requestChannel).basicAck(11L, false);
        verify(usageChannel).basicAck(12L, false);
    }

    private RequestCompletedEvent requestEvent() {
        return new RequestCompletedEvent(
                "evt-request-1",
                "req-1",
                1L,
                2L,
                3L,
                4L,
                5L,
                200,
                120,
                null,
                Instant.parse("2026-07-13T00:00:00Z"),
                1,
                "trace-1");
    }

    private UsageRecordedEvent usageEvent() {
        return new UsageRecordedEvent(
                "evt-usage-1",
                "req-1",
                1L,
                5L,
                4L,
                10,
                20,
                30,
                new BigDecimal("0.001"),
                "PROVIDER",
                Instant.parse("2026-07-13T00:00:00Z"),
                1,
                "trace-1");
    }
}
