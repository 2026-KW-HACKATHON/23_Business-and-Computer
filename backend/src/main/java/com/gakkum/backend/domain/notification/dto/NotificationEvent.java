package com.gakkum.backend.domain.notification.dto;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.gakkum.backend.domain.notification.entity.Notification;
import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;

/** 수신자 한 명의 알림 생성 값. 재발행할 때도 같은 eventId를 사용한다. */
public record NotificationEvent(UUID eventId, String recipientUserId, NotificationType type, String title,
        String body, NotificationTargetType targetType, String targetId) {

    public NotificationEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
    }

    public Map<String, String> toMap() {
        return Map.of("eventId", eventId.toString(), "recipientUserId", recipientUserId, "type", type.name(),
                "title", title, "body", body, "targetType", targetType.name(), "targetId", targetId);
    }

    public static NotificationEvent from(Map<String, String> fields) {
        return new NotificationEvent(UUID.fromString(fields.get("eventId")), fields.get("recipientUserId"),
                NotificationType.valueOf(fields.get("type")), fields.get("title"), fields.get("body"),
                NotificationTargetType.valueOf(fields.get("targetType")), fields.get("targetId"));
    }

    public Notification toEntity() {
        return Notification.create(eventId, recipientUserId, type, title, body, targetType, targetId);
    }
}
