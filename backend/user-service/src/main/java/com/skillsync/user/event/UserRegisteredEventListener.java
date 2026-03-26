package com.skillsync.user.event;

import com.skillsync.common.event.UserRegisteredEvent;
import com.skillsync.user.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UserRegisteredEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRegisteredEventListener.class);

    private final ProfileService profileService;

    public UserRegisteredEventListener(ProfileService profileService) {
        this.profileService = profileService;
    }

    @RabbitListener(queues = "user.registered.queue")
    public void handleUserRegistered(UserRegisteredEvent event) {
        LOGGER.info(
                "Processing UserRegisteredEvent userId={} correlationId={}",
                event.userId(),
                event.base() != null ? event.base().correlationId() : null
        );

        if (profileService.profileExists(event.userId())) {
            LOGGER.warn("Profile already exists for userId={}", event.userId());
            return;
        }

        profileService.createProfileForNewUser(event.userId(), event.email(), event.name());
    }
}
