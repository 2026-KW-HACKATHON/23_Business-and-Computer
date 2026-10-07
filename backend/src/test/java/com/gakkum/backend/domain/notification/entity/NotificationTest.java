package com.gakkum.backend.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final UUID EVENT_ID = UUID.fromString("0198c2f4-6a1e-7b3c-9d2e-4f5a6b7c8d9e");
    private static final String RECIPIENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String TITLE = "새 지원자가 있어요";
    private static final String BODY = "'간판 디자인' 의뢰에 새 지원자가 생겼어요.";
    private static final String TARGET_ID = "42";

    @Test
    @DisplayName("알림 생성 시 이벤트의 7개 값을 그대로 보존하고 미읽음 상태로 시작한다")
    void createsUnreadNotificationFromEvent() {
        Notification notification = notification();

        assertThat(notification.getEventId()).isEqualTo(EVENT_ID);
        assertThat(notification.getRecipientUserId()).isEqualTo(RECIPIENT_USER_ID);
        assertThat(notification.getType()).isEqualTo(NotificationType.JOB_APPLICATION_RECEIVED);
        assertThat(notification.getTitle()).isEqualTo(TITLE);
        assertThat(notification.getBody()).isEqualTo(BODY);
        assertThat(notification.getTargetType()).isEqualTo(NotificationTargetType.JOB);
        assertThat(notification.getTargetId()).isEqualTo(TARGET_ID);
        assertThat(notification.getReadAt()).isNull();
        // 생성 시각은 이벤트로 받지 않고 저장 시점에 기록한다
        assertThat(notification.getCreatedAt()).isNull();
    }

    @Test
    @DisplayName("알림 생성은 필수 인자가 null이면 거부한다")
    void rejectsNullArguments() {
        NotificationType type = NotificationType.JOB_APPLICATION_RECEIVED;
        NotificationTargetType targetType = NotificationTargetType.JOB;

        assertThatThrownBy(() -> Notification.create(
                null, RECIPIENT_USER_ID, type, TITLE, BODY, targetType, TARGET_ID))
                .isInstanceOf(NullPointerException.class).hasMessage("eventId");
        assertThatThrownBy(() -> Notification.create(EVENT_ID, null, type, TITLE, BODY, targetType, TARGET_ID))
                .isInstanceOf(NullPointerException.class).hasMessage("recipientUserId");
        assertThatThrownBy(() -> Notification.create(
                EVENT_ID, RECIPIENT_USER_ID, null, TITLE, BODY, targetType, TARGET_ID))
                .isInstanceOf(NullPointerException.class).hasMessage("type");
        assertThatThrownBy(() -> Notification.create(
                EVENT_ID, RECIPIENT_USER_ID, type, null, BODY, targetType, TARGET_ID))
                .isInstanceOf(NullPointerException.class).hasMessage("title");
        assertThatThrownBy(() -> Notification.create(
                EVENT_ID, RECIPIENT_USER_ID, type, TITLE, null, targetType, TARGET_ID))
                .isInstanceOf(NullPointerException.class).hasMessage("body");
        assertThatThrownBy(() -> Notification.create(EVENT_ID, RECIPIENT_USER_ID, type, TITLE, BODY, null, TARGET_ID))
                .isInstanceOf(NullPointerException.class).hasMessage("targetType");
        assertThatThrownBy(() -> Notification.create(EVENT_ID, RECIPIENT_USER_ID, type, TITLE, BODY, targetType, null))
                .isInstanceOf(NullPointerException.class).hasMessage("targetId");
    }

    @Test
    @DisplayName("읽음 처리는 최초 시각을 기록한다")
    void recordsFirstReadTime() {
        Notification notification = notification();
        LocalDateTime readAt = LocalDateTime.of(2026, 10, 7, 9, 30);

        notification.markRead(readAt);

        assertThat(notification.getReadAt()).isEqualTo(readAt);
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽음 처리해도 최초 읽음 시각을 유지한다")
    void keepsFirstReadTimeOnRepeatedCalls() {
        Notification notification = notification();
        LocalDateTime firstReadAt = LocalDateTime.of(2026, 10, 7, 9, 30);
        notification.markRead(firstReadAt);

        notification.markRead(firstReadAt.plusHours(3));
        notification.markRead(firstReadAt.minusHours(3));

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    @DisplayName("읽음 시각이 null이면 거부하고 기존 읽음 상태를 바꾸지 않는다")
    void rejectsNullReadTime() {
        Notification unread = notification();
        Notification read = notification();
        LocalDateTime readAt = LocalDateTime.of(2026, 10, 7, 9, 30);
        read.markRead(readAt);

        assertThatThrownBy(() -> unread.markRead(null)).isInstanceOf(NullPointerException.class).hasMessage("readAt");
        assertThatThrownBy(() -> read.markRead(null)).isInstanceOf(NullPointerException.class).hasMessage("readAt");

        assertThat(unread.getReadAt()).isNull();
        assertThat(read.getReadAt()).isEqualTo(readAt);
    }

    private Notification notification() {
        return Notification.create(EVENT_ID, RECIPIENT_USER_ID, NotificationType.JOB_APPLICATION_RECEIVED, TITLE,
                BODY, NotificationTargetType.JOB, TARGET_ID);
    }
}
