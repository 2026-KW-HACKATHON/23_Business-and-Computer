package com.gakkum.backend.application.notification.dto;

import java.time.LocalDateTime;

import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationReadResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationReadResponse {

    private final Long notificationId;
    private final LocalDateTime readAt;

    public static NotificationReadResponse from(NotificationReadResult result) {
        return NotificationReadResponse.builder()
                .notificationId(result.getNotificationId())
                .readAt(result.getReadAt())
                .build();
    }
}
