package com.skillsync.mentor.config;

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

    private static final String MENTOR_REVIEW_DLQ = "mentor.review.queue.dlq";
    private static final String MENTOR_WAITLIST_DLQ = "mentor.waitlist.queue.dlq";

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
    public Queue mentorReviewQueue() {
        return QueueBuilder.durable(RabbitMQConstants.MENTOR_REVIEW_QUEUE)
                .deadLetterExchange(RabbitMQConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MENTOR_REVIEW_DLQ)
                .build();
    }

    @Bean
    public Queue mentorReviewDeadLetterQueue() {
        return QueueBuilder.durable(MENTOR_REVIEW_DLQ).build();
    }

    @Bean
    public Queue mentorWaitlistQueue() {
        return QueueBuilder.durable(RabbitMQConstants.MENTOR_WAITLIST_QUEUE)
                .deadLetterExchange(RabbitMQConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MENTOR_WAITLIST_DLQ)
                .build();
    }

    @Bean
    public Queue mentorWaitlistDeadLetterQueue() {
        return QueueBuilder.durable(MENTOR_WAITLIST_DLQ).build();
    }

    @Bean
    public Binding mentorReviewBinding(Queue mentorReviewQueue, TopicExchange skillsyncEventsExchange) {
        return BindingBuilder.bind(mentorReviewQueue)
                .to(skillsyncEventsExchange)
                .with(RabbitMQConstants.RK_REVIEW_SUBMITTED);
    }

    @Bean
    public Binding mentorReviewDeadLetterBinding(
            Queue mentorReviewDeadLetterQueue,
            TopicExchange skillsyncDeadLetterExchange
    ) {
        return BindingBuilder.bind(mentorReviewDeadLetterQueue)
                .to(skillsyncDeadLetterExchange)
                .with(MENTOR_REVIEW_DLQ);
    }

    @Bean
    public Binding mentorWaitlistBinding(Queue mentorWaitlistQueue, TopicExchange skillsyncEventsExchange) {
        return BindingBuilder.bind(mentorWaitlistQueue)
                .to(skillsyncEventsExchange)
                .with(RabbitMQConstants.RK_SESSION_CANCELLED);
    }

    @Bean
    public Binding mentorWaitlistDeadLetterBinding(
            Queue mentorWaitlistDeadLetterQueue,
            TopicExchange skillsyncDeadLetterExchange
    ) {
        return BindingBuilder.bind(mentorWaitlistDeadLetterQueue)
                .to(skillsyncDeadLetterExchange)
                .with(MENTOR_WAITLIST_DLQ);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
