package com.skillsync.notification.mapper;

import com.skillsync.notification.dto.NotificationResponse;
import com.skillsync.notification.model.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toNotificationResponse(Notification notification);
}
