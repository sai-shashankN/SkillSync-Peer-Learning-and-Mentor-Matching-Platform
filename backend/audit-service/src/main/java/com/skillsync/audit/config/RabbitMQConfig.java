package com.skillsync.audit.config;

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
    public Queue auditQueue() {
        return QueueBuilder.durable(RabbitMQConstants.AUDIT_QUEUE)
                .deadLetterExchange(RabbitMQConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitMQConstants.AUDIT_DLQ)
                .build();
    }

    @Bean
    public Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMQConstants.AUDIT_DLQ).build();
    }

    @Bean
    public Binding auditBinding(Queue auditQueue, TopicExchange skillsyncEventsExchange) {
        return BindingBuilder.bind(auditQueue)
                .to(skillsyncEventsExchange)
                .with("event.#");
    }

    @Bean
    public Binding auditDeadLetterBinding(Queue auditDeadLetterQueue, TopicExchange skillsyncDeadLetterExchange) {
        return BindingBuilder.bind(auditDeadLetterQueue)
                .to(skillsyncDeadLetterExchange)
                .with(RabbitMQConstants.AUDIT_DLQ);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
