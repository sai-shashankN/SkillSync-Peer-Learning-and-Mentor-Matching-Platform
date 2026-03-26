package com.skillsync.payment.config;

import com.skillsync.common.config.RabbitMQConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private static final String PAYMENT_SESSION_COMPLETED_DLQ = "payment.session.completed.queue.dlq";

    @Bean
    public TopicExchange skillsyncEventsExchange() {
        return new TopicExchange(RabbitMQConstants.EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange skillsyncDeadLetterExchange() {
        return new TopicExchange(RabbitMQConstants.DLX_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange skillsyncRetryExchange() {
        return new TopicExchange(RabbitMQConstants.RETRY_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentSessionQueue() {
        return QueueBuilder.durable(RabbitMQConstants.PAYMENT_SESSION_QUEUE)
                .deadLetterExchange(RabbitMQConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitMQConstants.PAYMENT_SESSION_DLQ)
                .build();
    }

    @Bean
    public Queue paymentSessionDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMQConstants.PAYMENT_SESSION_DLQ).build();
    }

    @Bean
    public Queue paymentSessionCompletedQueue() {
        return QueueBuilder.durable(RabbitMQConstants.PAYMENT_SESSION_COMPLETED_QUEUE)
                .deadLetterExchange(RabbitMQConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_SESSION_COMPLETED_DLQ)
                .build();
    }

    @Bean
    public Queue paymentSessionCompletedDeadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_SESSION_COMPLETED_DLQ).build();
    }

    @Bean
    public Binding paymentSessionBinding(Queue paymentSessionQueue, TopicExchange skillsyncEventsExchange) {
        return BindingBuilder.bind(paymentSessionQueue)
                .to(skillsyncEventsExchange)
                .with(RabbitMQConstants.RK_SESSION_BOOKED);
    }

    @Bean
    public Binding paymentSessionDeadLetterBinding(
            Queue paymentSessionDeadLetterQueue,
            TopicExchange skillsyncDeadLetterExchange
    ) {
        return BindingBuilder.bind(paymentSessionDeadLetterQueue)
                .to(skillsyncDeadLetterExchange)
                .with(RabbitMQConstants.PAYMENT_SESSION_DLQ);
    }

    @Bean
    public Binding paymentSessionCompletedBinding(
            Queue paymentSessionCompletedQueue,
            TopicExchange skillsyncEventsExchange
    ) {
        return BindingBuilder.bind(paymentSessionCompletedQueue)
                .to(skillsyncEventsExchange)
                .with(RabbitMQConstants.RK_SESSION_COMPLETED);
    }

    @Bean
    public Binding paymentSessionCompletedDeadLetterBinding(
            Queue paymentSessionCompletedDeadLetterQueue,
            TopicExchange skillsyncDeadLetterExchange
    ) {
        return BindingBuilder.bind(paymentSessionCompletedDeadLetterQueue)
                .to(skillsyncDeadLetterExchange)
                .with(PAYMENT_SESSION_COMPLETED_DLQ);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
