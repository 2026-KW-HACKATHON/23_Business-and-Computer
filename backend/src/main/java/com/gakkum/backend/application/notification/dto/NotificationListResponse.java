package com.gakkum.backend.application.notification.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationListResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationResult;
import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 알림 목록 한 페이지. 다음 페이지가 없으면 nextCursor는 null이다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationListResponse {

    private final List<Item> items;
    private final String nextCursor;

    public static NotificationListResponse from(NotificationListResult result) {
        return new NotificationListResponse(
                result.getItems().stream().map(Item::from).toList(),
                result.getNextCursor());
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Item {

        private final Long id;
        private final NotificationType type;
        private final String title;
        private final String body;
        private final NotificationTargetType targetType;
        private final String targetId;
        // null이면 미읽음
        private final LocalDateTime readAt;
        private final LocalDateTime createdAt;

        public static Item from(NotificationResult result) {
            return Item.builder()
                    .id(result.getId())
                    .type(result.getType())
                    .title(result.getTitle())
                    .body(result.getBody())
                    .targetType(result.getTargetType())
                    .targetId(result.getTargetId())
                    .readAt(result.getReadAt())
                    .createdAt(result.getCreatedAt())
                    .build();
        }
    }
}
