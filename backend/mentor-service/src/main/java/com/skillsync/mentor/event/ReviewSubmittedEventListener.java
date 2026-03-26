package com.skillsync.mentor.event;

import com.skillsync.common.config.RabbitMQConstants;
import com.skillsync.common.event.ReviewSubmittedEvent;
import com.skillsync.mentor.service.MentorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSubmittedEventListener {

    private final MentorService mentorService;

    @RabbitListener(queues = RabbitMQConstants.MENTOR_REVIEW_QUEUE)
    public void handleReviewSubmitted(ReviewSubmittedEvent event) {
        log.info("Processing review submitted event for mentorId={}, reviewId={}", event.mentorId(), event.reviewId());
        mentorService.updateRating(event.mentorId(), event.rating());
    }
}
