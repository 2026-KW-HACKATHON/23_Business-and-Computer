package com.gakkum.backend.application.notification.dto;

import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationUnreadCountResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationUnreadCountResponse {

    private final long unreadCount;

    public static NotificationUnreadCountResponse from(NotificationUnreadCountResult result) {
        return NotificationUnreadCountResponse.builder()
                .unreadCount(result.getUnreadCount())
                .build();
    }
}
