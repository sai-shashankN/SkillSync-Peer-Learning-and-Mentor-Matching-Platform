package com.skillsync.auth.service;

import com.skillsync.auth.model.User;
import com.skillsync.common.config.RabbitMQConstants;
import com.skillsync.common.event.BaseEvent;
import com.skillsync.common.event.UserRegisteredEvent;
import com.skillsync.common.util.EventFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventPublisherService {

    private static final String SERVICE_NAME = "auth-service";

    private final RabbitTemplate rabbitTemplate;

    public EventPublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUserRegistered(User user) {
        BaseEvent baseEvent = EventFactory.create(
                RabbitMQConstants.RK_USER_REGISTERED,
                SERVICE_NAME,
                MDC.get("correlationId")
        );
        UserRegisteredEvent event = new UserRegisteredEvent(baseEvent, user.getId(), user.getEmail(), user.getName());
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_USER_REGISTERED, event);
    }
}
