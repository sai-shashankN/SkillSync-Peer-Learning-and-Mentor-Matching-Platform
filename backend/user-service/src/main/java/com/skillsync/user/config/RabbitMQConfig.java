package com.skillsync.user.config;

import com.skillsync.common.config.RabbitMQConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private static final String USER_REGISTERED_QUEUE = "user.registered.queue";
    private static final String USER_REGISTERED_DLQ = "user.registered.queue.dlq";

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
    public Queue userRegisteredQueue() {
        return QueueBuilder.durable(USER_REGISTERED_QUEUE)
                .deadLetterExchange(RabbitMQConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(USER_REGISTERED_DLQ)
                .build();
    }

    @Bean
    public Queue userRegisteredDeadLetterQueue() {
        return QueueBuilder.durable(USER_REGISTERED_DLQ).build();
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, TopicExchange skillsyncEventsExchange) {
        return BindingBuilder.bind(userRegisteredQueue)
                .to(skillsyncEventsExchange)
                .with(RabbitMQConstants.RK_USER_REGISTERED);
    }

    @Bean
    public Binding userRegisteredDeadLetterBinding(
            Queue userRegisteredDeadLetterQueue,
            TopicExchange skillsyncDeadLetterExchange
    ) {
        return BindingBuilder.bind(userRegisteredDeadLetterQueue)
                .to(skillsyncDeadLetterExchange)
                .with(USER_REGISTERED_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
