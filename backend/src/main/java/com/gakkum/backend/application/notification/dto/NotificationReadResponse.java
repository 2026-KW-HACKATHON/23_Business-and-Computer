package com.gakkum.backend.application.notification.dto;

import java.time.OffsetDateTime;

import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationReadResult;
import com.gakkum.backend.global.response.KoreaTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationReadResponse {

    private final Long notificationId;
    private final OffsetDateTime readAt;

    public static NotificationReadResponse from(NotificationReadResult result) {
        return NotificationReadResponse.builder()
                .notificationId(result.getNotificationId())
                .readAt(KoreaTime.from(result.getReadAt()))
                .build();
    }
}
