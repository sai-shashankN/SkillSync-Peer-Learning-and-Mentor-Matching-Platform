package com.skillsync.review.config;

import com.skillsync.common.config.RabbitMQConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private static final String REVIEW_SESSION_DLQ = "review.session.queue.dlq";

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
    public Queue reviewSessionQueue() {
        return QueueBuilder.durable(RabbitMQConstants.REVIEW_SESSION_QUEUE)
                .deadLetterExchange(RabbitMQConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(REVIEW_SESSION_DLQ)
                .build();
    }

    @Bean
    public Queue reviewSessionDeadLetterQueue() {
        return QueueBuilder.durable(REVIEW_SESSION_DLQ).build();
    }

    @Bean
    public Binding reviewSessionBinding(Queue reviewSessionQueue, TopicExchange skillsyncEventsExchange) {
        return BindingBuilder.bind(reviewSessionQueue)
                .to(skillsyncEventsExchange)
                .with(RabbitMQConstants.RK_SESSION_COMPLETED);
    }

    @Bean
    public Binding reviewSessionDeadLetterBinding(
            Queue reviewSessionDeadLetterQueue,
            TopicExchange skillsyncDeadLetterExchange
    ) {
        return BindingBuilder.bind(reviewSessionDeadLetterQueue)
                .to(skillsyncDeadLetterExchange)
                .with(REVIEW_SESSION_DLQ);
    }
}
