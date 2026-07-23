package com.example.aigateway.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares the durable exchange, queues, bindings, and JSON converter for gateway events. */
@Configuration
public class RabbitTopology {
    public static final String EVENTS_EXCHANGE = "ai-gateway.events";

    public static final String REQUEST_LOG_QUEUE = "ai-gateway.request-log.queue";
    public static final String USAGE_STATISTICS_QUEUE = "ai-gateway.usage-statistics.queue";
    public static final String NOTIFICATION_QUEUE = "ai-gateway.notification.queue";

    public static final String REQUEST_COMPLETED = "request.completed";
    public static final String USAGE_RECORDED = "usage.recorded";
    public static final String NOTIFICATION_REQUESTED = "notification.requested";

    @Bean
    public TopicExchange gatewayEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue requestLogQueue() {
        return QueueBuilder.durable(REQUEST_LOG_QUEUE).build();
    }

    @Bean
    public Queue usageStatisticsQueue() {
        return QueueBuilder.durable(USAGE_STATISTICS_QUEUE).build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Binding requestLogBinding(
            @Qualifier("requestLogQueue") Queue queue,
            TopicExchange gatewayEventsExchange) {
        return BindingBuilder.bind(queue)
                .to(gatewayEventsExchange)
                .with("request.#");
    }

    @Bean
    public Binding usageStatisticsBinding(
            @Qualifier("usageStatisticsQueue") Queue queue,
            TopicExchange gatewayEventsExchange) {
        return BindingBuilder.bind(queue)
                .to(gatewayEventsExchange)
                .with("usage.#");
    }

    @Bean
    public Binding notificationBinding(
            @Qualifier("notificationQueue") Queue queue,
            TopicExchange gatewayEventsExchange) {
        return BindingBuilder.bind(queue)
                .to(gatewayEventsExchange)
                .with("notification.#");
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
