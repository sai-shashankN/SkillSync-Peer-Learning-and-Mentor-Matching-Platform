package com.skillsync.mentor.event;

import com.skillsync.common.config.RabbitMQConstants;
import com.skillsync.common.event.SessionCancelledEvent;
import com.skillsync.mentor.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCancelledEventListener {

    private final WaitlistService waitlistService;

    @RabbitListener(queues = RabbitMQConstants.MENTOR_WAITLIST_QUEUE)
    public void handleSessionCancelled(SessionCancelledEvent event) {
        Long mentorId = extractMentorId(event);
        if (mentorId == null) {
            log.warn(
                    "Session cancelled event missing mentorId. sessionId={}, cancelledBy={}",
                    event.sessionId(),
                    event.cancelledBy()
            );
            return;
        }

        waitlistService.notifyNextWaitlistedLearner(mentorId);
        log.info("Processed session cancelled event for mentorId={}, sessionId={}", mentorId, event.sessionId());
    }

    private Long extractMentorId(SessionCancelledEvent event) {
        if (event.base() == null || event.base().metadata() == null) {
            return null;
        }

        Object mentorId = event.base().metadata().get("mentorId");
        if (mentorId instanceof Number number) {
            return number.longValue();
        }
        if (mentorId instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
