package com.skillsync.mentor.service;

import com.skillsync.common.config.RabbitMQConstants;
import com.skillsync.common.event.BaseEvent;
import com.skillsync.common.event.MentorAppliedEvent;
import com.skillsync.common.event.MentorApprovedEvent;
import com.skillsync.common.event.MentorRejectedEvent;
import com.skillsync.common.event.WaitlistSlotOpenEvent;
import com.skillsync.common.util.EventFactory;
import com.skillsync.mentor.model.Mentor;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventPublisherService {

    private static final String SERVICE_NAME = "mentor-service";

    private final RabbitTemplate rabbitTemplate;

    public void publishMentorApplied(Mentor mentor, String userName) {
        BaseEvent baseEvent = EventFactory.create(
                RabbitMQConstants.RK_MENTOR_APPLIED,
                SERVICE_NAME,
                MDC.get("correlationId")
        );
        MentorAppliedEvent event = new MentorAppliedEvent(baseEvent, mentor.getId(), mentor.getUserId(), userName);
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_MENTOR_APPLIED, event);
    }

    public void publishMentorApproved(Mentor mentor, String email) {
        BaseEvent baseEvent = EventFactory.create(
                RabbitMQConstants.RK_MENTOR_APPROVED,
                SERVICE_NAME,
                MDC.get("correlationId")
        );
        MentorApprovedEvent event = new MentorApprovedEvent(baseEvent, mentor.getId(), mentor.getUserId(), email);
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_MENTOR_APPROVED, event);
    }

    public void publishMentorRejected(Mentor mentor, String email) {
        BaseEvent baseEvent = EventFactory.create(
                RabbitMQConstants.RK_MENTOR_REJECTED,
                SERVICE_NAME,
                MDC.get("correlationId")
        );
        MentorRejectedEvent event = new MentorRejectedEvent(baseEvent, mentor.getId(), mentor.getUserId(), email);
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_MENTOR_REJECTED, event);
    }

    public void publishWaitlistSlotOpen(Long mentorId, Long learnerId) {
        BaseEvent baseEvent = EventFactory.create(
                RabbitMQConstants.RK_WAITLIST_SLOT_OPEN,
                SERVICE_NAME,
                MDC.get("correlationId")
        );
        WaitlistSlotOpenEvent event = new WaitlistSlotOpenEvent(baseEvent, mentorId, learnerId);
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_WAITLIST_SLOT_OPEN, event);
    }
}
