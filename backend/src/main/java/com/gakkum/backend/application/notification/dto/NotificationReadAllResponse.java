package com.gakkum.backend.application.notification.dto;

import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationReadAllResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationReadAllResponse {

    private final int updatedCount;

    public static NotificationReadAllResponse from(NotificationReadAllResult result) {
        return NotificationReadAllResponse.builder()
                .updatedCount(result.getUpdatedCount())
                .build();
    }
}
