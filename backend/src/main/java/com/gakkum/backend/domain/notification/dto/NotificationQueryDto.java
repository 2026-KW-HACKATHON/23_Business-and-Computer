package com.gakkum.backend.domain.notification.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.gakkum.backend.domain.notification.entity.Notification;
import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class NotificationQueryDto {

    private NotificationQueryDto() {
    }

    /** 알림 목록 한 페이지. 다음 페이지가 없으면 nextCursor는 null이다. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class NotificationListResult {

        private final List<NotificationResult> items;
        private final String nextCursor;

        public static NotificationListResult of(List<NotificationResult> items, String nextCursor) {
            return new NotificationListResult(List.copyOf(items), nextCursor);
        }
    }

    /** 알림 한 건. 원인 이벤트 ID와 수신자 ID는 담지 않는다. readAt이 null이면 미읽음이다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class NotificationResult {

        private final Long id;
        private final NotificationType type;
        private final String title;
        private final String body;
        private final NotificationTargetType targetType;
        private final String targetId;
        private final LocalDateTime readAt;
        private final LocalDateTime createdAt;

        public static NotificationResult from(Notification notification) {
            return NotificationResult.builder()
                    .id(notification.getId())
                    .type(notification.getType())
                    .title(notification.getTitle())
                    .body(notification.getBody())
                    .targetType(notification.getTargetType())
                    .targetId(notification.getTargetId())
                    .readAt(notification.getReadAt())
                    .createdAt(notification.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class NotificationUnreadCountResult {

        private final long unreadCount;

        public static NotificationUnreadCountResult of(long unreadCount) {
            return new NotificationUnreadCountResult(unreadCount);
        }
    }

    /** 개별 읽음 결과. 반복 요청에도 최초 읽음 시각을 내린다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class NotificationReadResult {

        private final Long notificationId;
        private final LocalDateTime readAt;

        public static NotificationReadResult from(Notification notification) {
            return NotificationReadResult.builder()
                    .notificationId(notification.getId())
                    .readAt(notification.getReadAt())
                    .build();
        }
    }

    /** 모두 읽음 결과. updatedCount는 이번 요청이 읽음으로 바꾼 알림 수다. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class NotificationReadAllResult {

        private final int updatedCount;

        public static NotificationReadAllResult of(int updatedCount) {
            return new NotificationReadAllResult(updatedCount);
        }
    }
}
