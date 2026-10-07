package com.gakkum.backend.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import com.gakkum.backend.domain.notification.dto.NotificationCommandDto.GetNotificationsCommand;
import com.gakkum.backend.domain.notification.entity.Notification;
import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;
import com.gakkum.backend.domain.notification.repository.NotificationRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("알림 서비스 (목록 조회·미읽음 개수·읽음 처리)")
class NotificationServiceTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String RECIPIENT = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    // 나노초까지 있는 시각. 저장 정밀도인 마이크로초로 잘라 기록해야 한다
    private static final Instant NOW = Instant.parse("2026-10-07T02:03:04.123456789Z");
    private static final LocalDateTime NOW_LOCAL =
            LocalDateTime.ofInstant(Instant.parse("2026-10-07T02:03:04.123456Z"), ZoneOffset.UTC);
    private static final LocalDateTime CURSOR_CREATED_AT = LocalDateTime.of(2026, 10, 7, 10, 30);

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationService service =
            new NotificationService(notificationRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("커서가 없으면 수신자의 첫 페이지를 요청 크기보다 하나 더 읽는다")
    void readsFirstPageWithOneExtraRow() {
        List<Notification> rows = List.of(notification(2L), notification(1L));
        when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDescIdDesc(RECIPIENT, Limit.of(21)))
                .thenReturn(rows);

        List<Notification> result = service.getNotifications(RECIPIENT,
                GetNotificationsCommand.of(USERNAME, 20, null, null));

        assertThat(result).isEqualTo(rows);
        verify(notificationRepository).findByRecipientUserIdOrderByCreatedAtDescIdDesc(RECIPIENT, Limit.of(21));
        verifyNoMoreInteractions(notificationRepository);
    }

    @Test
    @DisplayName("커서가 있으면 수신자의 알림 중 커서 뒤부터 요청 크기보다 하나 더 읽는다")
    void readsPageAfterCursorWithOneExtraRow() {
        List<Notification> rows = List.of(notification(1L));
        when(notificationRepository.findPageAfterCursor(RECIPIENT, CURSOR_CREATED_AT, 42L, Limit.of(6)))
                .thenReturn(rows);

        List<Notification> result = service.getNotifications(RECIPIENT,
                GetNotificationsCommand.of(USERNAME, 5, CURSOR_CREATED_AT, 42L));

        assertThat(result).isEqualTo(rows);
        verify(notificationRepository).findPageAfterCursor(RECIPIENT, CURSOR_CREATED_AT, 42L, Limit.of(6));
        verifyNoMoreInteractions(notificationRepository);
    }

    @Test
    @DisplayName("미읽음 개수는 수신자의 읽음 시각이 비어 있는 알림만 센다")
    void countsUnreadOfRecipient() {
        when(notificationRepository.countByRecipientUserIdAndReadAtIsNull(RECIPIENT)).thenReturn(3L);

        assertThat(service.countUnread(RECIPIENT)).isEqualTo(3L);
        verify(notificationRepository).countByRecipientUserIdAndReadAtIsNull(RECIPIENT);
        verifyNoMoreInteractions(notificationRepository);
    }

    @Test
    @DisplayName("미읽음 알림을 잠근 뒤 서버 시각을 JVM 기본 시간대·마이크로초 정밀도의 읽음 시각으로 기록한다")
    void recordsFirstReadTime() {
        Notification notification = notification(31L);
        when(notificationRepository.findLockedByIdAndRecipientUserId(31L, RECIPIENT))
                .thenReturn(Optional.of(notification));

        Notification result = service.markRead(RECIPIENT, 31L);

        assertThat(result).isSameAs(notification);
        assertThat(result.getReadAt()).isEqualTo(NOW_LOCAL);
        verify(notificationRepository).findLockedByIdAndRecipientUserId(31L, RECIPIENT);
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽음 처리해도 최초 읽음 시각을 그대로 반환한다")
    void keepsFirstReadTimeOnRepeat() {
        LocalDateTime firstReadAt = LocalDateTime.of(2026, 10, 1, 9, 0);
        Notification notification = notification(31L);
        notification.markRead(firstReadAt);
        when(notificationRepository.findLockedByIdAndRecipientUserId(31L, RECIPIENT))
                .thenReturn(Optional.of(notification));

        assertThat(service.markRead(RECIPIENT, 31L).getReadAt()).isEqualTo(firstReadAt);
        assertThat(service.markRead(RECIPIENT, 31L).getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    @DisplayName("수신자 조건으로 찾지 못한 알림은 없는 알림이든 다른 수신자의 알림이든 같은 NOTIFICATION_404로 거부한다")
    void rejectsNotificationNotFoundForRecipient() {
        when(notificationRepository.findLockedByIdAndRecipientUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(RECIPIENT, 31L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));
        // 알림 ID만으로 찾지 않는다
        verify(notificationRepository).findLockedByIdAndRecipientUserId(31L, RECIPIENT);
        verify(notificationRepository, never()).findById(any());
    }

    @Test
    @DisplayName("모두 읽음은 수신자의 미읽음 알림을 서버 시각으로 한 번에 갱신하고 변경한 행 수를 반환한다")
    void marksAllUnreadAtOnce() {
        when(notificationRepository.markAllRead(RECIPIENT, NOW_LOCAL)).thenReturn(4, 0);

        assertThat(service.markAllRead(RECIPIENT)).isEqualTo(4);
        assertThat(service.markAllRead(RECIPIENT)).isZero();
        // 조회 후 행별로 갱신하지 않는다
        verify(notificationRepository, never()).findLockedByIdAndRecipientUserId(any(), any());
        verify(notificationRepository, never()).findByRecipientUserIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    private static Notification notification(Long id) {
        Notification notification = Notification.create(UUID.randomUUID(), RECIPIENT,
                NotificationType.JOB_APPLICATION_RECEIVED, "새 지원자가 있어요", "본문",
                NotificationTargetType.JOB, "42");
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }
}
