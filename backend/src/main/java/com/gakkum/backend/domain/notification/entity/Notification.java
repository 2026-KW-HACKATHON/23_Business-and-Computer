package com.gakkum.backend.domain.notification.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(
        name = "notifications_event_id_recipient_user_id_key", columnNames = { "event_id", "recipient_user_id" }))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 원인 이벤트 식별자. 같은 이벤트의 재전달을 수신자별로 한 번만 저장한다
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "recipient_user_id", nullable = false, updatable = false, length = 26)
    private String recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, updatable = false, length = 255)
    private String title;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 50)
    private NotificationTargetType targetType;

    // 대상 도메인에 따라 숫자 ID 또는 ULID를 문자열로 저장한다
    @Column(name = "target_id", nullable = false, updatable = false, length = 26)
    private String targetId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    // 화면 시간과 목록 정렬의 기준. 이벤트 발생 시각이 아니라 알림을 저장한 시각이다
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 이벤트 계약의 값을 그대로 담아 미읽음 상태로 만든다. */
    public static Notification create(UUID eventId, String recipientUserId, NotificationType type, String title,
            String body, NotificationTargetType targetType, String targetId) {
        Notification notification = new Notification();
        notification.eventId = Objects.requireNonNull(eventId, "eventId");
        notification.recipientUserId = Objects.requireNonNull(recipientUserId, "recipientUserId");
        notification.type = Objects.requireNonNull(type, "type");
        notification.title = Objects.requireNonNull(title, "title");
        notification.body = Objects.requireNonNull(body, "body");
        notification.targetType = Objects.requireNonNull(targetType, "targetType");
        notification.targetId = Objects.requireNonNull(targetId, "targetId");
        return notification;
    }

    /** 최초 읽음 시각만 기록한다. 이미 읽은 알림은 기존 시각을 유지한다. */
    public void markRead(LocalDateTime readAt) {
        Objects.requireNonNull(readAt, "readAt");
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }
}
