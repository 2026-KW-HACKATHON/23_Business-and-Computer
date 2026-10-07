package com.gakkum.backend.application.notification.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.gakkum.backend.application.notification.dto.NotificationCursor;
import com.gakkum.backend.domain.notification.dto.NotificationCommandDto.GetNotificationsCommand;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationListResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationReadResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationResult;
import com.gakkum.backend.domain.notification.entity.Notification;
import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;
import com.gakkum.backend.domain.notification.service.NotificationService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("알림 파사드 (사용자 확인·커서 페이지·읽음 처리 위임)")
class NotificationFacadeTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 7, 10, 30, 0, 123_456_000);
    private static final LocalDateTime READ_AT = LocalDateTime.of(2026, 10, 7, 11, 0, 15);

    private final UserService userService = mock(UserService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationFacade facade = new NotificationFacade(userService, notificationService);

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = { "STUDENT", "OWNER" })
    @DisplayName("학생과 사장님 모두 역할 제한 없이 username이 아닌 실제 사용자 ID로 본인 알림을 조회한다")
    void readsOwnNotificationsByUserId(UserRole role) {
        givenUser(role);
        GetNotificationsCommand command = GetNotificationsCommand.of(USERNAME, 20, null, null);
        when(notificationService.getNotifications(USER_ID, command)).thenReturn(List.of(notification(3L, null)));
        when(notificationService.countUnread(USER_ID)).thenReturn(1L);

        NotificationListResult list = facade.getNotifications(command);

        assertThat(list.getItems()).extracting(NotificationResult::getId).containsExactly(3L);
        assertThat(facade.getUnreadCount(USERNAME).getUnreadCount()).isEqualTo(1L);
        verify(notificationService).getNotifications(USER_ID, command);
        verify(notificationService).countUnread(USER_ID);
        // 조회는 읽음 처리를 호출하지 않는다
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("요청 크기보다 한 건 더 읽히면 요청 크기만큼만 담고 마지막 항목의 생성 시각·ID로 다음 커서를 만든다")
    void buildsNextCursorFromLastItemOfPage() {
        givenUser(UserRole.STUDENT);
        GetNotificationsCommand command = GetNotificationsCommand.of(USERNAME, 2, null, null);
        when(notificationService.getNotifications(USER_ID, command)).thenReturn(notifications(9L, 8L, 7L));

        NotificationListResult result = facade.getNotifications(command);

        assertThat(result.getItems()).extracting(NotificationResult::getId).containsExactly(9L, 8L);
        NotificationCursor cursor = NotificationCursor.decode(result.getNextCursor());
        assertThat(cursor.getCreatedAt()).isEqualTo(CREATED_AT.minusMinutes(8));
        assertThat(cursor.getId()).isEqualTo(8L);
    }

    @Test
    @DisplayName("요청 크기 이하로 읽히면 마지막 페이지로 보고 다음 커서를 null로 내린다")
    void returnsNullCursorOnLastPage() {
        givenUser(UserRole.STUDENT);
        GetNotificationsCommand command = GetNotificationsCommand.of(USERNAME, 2, CREATED_AT, 10L);
        when(notificationService.getNotifications(USER_ID, command)).thenReturn(notifications(9L, 8L));

        NotificationListResult result = facade.getNotifications(command);

        assertThat(result.getItems()).extracting(NotificationResult::getId).containsExactly(9L, 8L);
        assertThat(result.getNextCursor()).isNull();
    }

    @Test
    @DisplayName("알림이 없으면 빈 목록과 null 커서를 반환한다")
    void returnsEmptyPage() {
        givenUser(UserRole.OWNER);
        GetNotificationsCommand command = GetNotificationsCommand.of(USERNAME, 20, null, null);
        when(notificationService.getNotifications(USER_ID, command)).thenReturn(List.of());

        NotificationListResult result = facade.getNotifications(command);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getNextCursor()).isNull();
    }

    @Test
    @DisplayName("목록 결과는 8개 값을 그대로 옮기고 미읽음의 읽음 시각은 null이다")
    void mapsNotificationFields() {
        givenUser(UserRole.STUDENT);
        GetNotificationsCommand command = GetNotificationsCommand.of(USERNAME, 20, null, null);
        when(notificationService.getNotifications(USER_ID, command))
                .thenReturn(List.of(notification(5L, READ_AT), notification(4L, null)));

        List<NotificationResult> items = facade.getNotifications(command).getItems();

        NotificationResult read = items.get(0);
        assertThat(read.getId()).isEqualTo(5L);
        assertThat(read.getType()).isEqualTo(NotificationType.PROPOSAL_RECEIVED);
        assertThat(read.getTitle()).isEqualTo("새 제안이 도착했어요");
        assertThat(read.getBody()).isEqualTo("본문 5");
        assertThat(read.getTargetType()).isEqualTo(NotificationTargetType.PROPOSAL);
        assertThat(read.getTargetId()).isEqualTo("105");
        assertThat(read.getReadAt()).isEqualTo(READ_AT);
        assertThat(read.getCreatedAt()).isEqualTo(CREATED_AT.minusMinutes(5));
        assertThat(items.get(1).getReadAt()).isNull();
    }

    @Test
    @DisplayName("개별 읽음은 실제 사용자 ID와 알림 ID로 처리하고 알림 ID와 읽음 시각을 반환한다")
    void marksReadByUserId() {
        givenUser(UserRole.OWNER);
        when(notificationService.markRead(USER_ID, 31L)).thenReturn(notification(31L, READ_AT));

        NotificationReadResult result = facade.markRead(USERNAME, 31L);

        assertThat(result.getNotificationId()).isEqualTo(31L);
        assertThat(result.getReadAt()).isEqualTo(READ_AT);
        verify(notificationService).markRead(USER_ID, 31L);
    }

    @Test
    @DisplayName("개별 읽음에서 없거나 다른 사용자의 알림이면 NOTIFICATION_404를 그대로 전달한다")
    void propagatesNotFound() {
        givenUser(UserRole.STUDENT);
        when(notificationService.markRead(USER_ID, 31L))
                .thenThrow(new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        assertError(() -> facade.markRead(USERNAME, 31L), ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("모두 읽음은 실제 사용자 ID로 처리하고 변경한 알림 수를 반환하며 대상이 없으면 0이다")
    void marksAllReadByUserId() {
        givenUser(UserRole.STUDENT);
        when(notificationService.markAllRead(USER_ID)).thenReturn(4, 0);

        assertThat(facade.markAllRead(USERNAME).getUpdatedCount()).isEqualTo(4);
        assertThat(facade.markAllRead(USERNAME).getUpdatedCount()).isZero();
    }

    @Test
    @DisplayName("잠겼거나 없는 사용자는 네 기능 모두 COMMON_401로 거부하고 알림을 조회·변경하지 않는다")
    void rejectsInactiveUser() {
        when(userService.getActiveUser(USERNAME)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertError(() -> facade.getNotifications(GetNotificationsCommand.of(USERNAME, 20, null, null)),
                ErrorCode.UNAUTHORIZED);
        assertError(() -> facade.getUnreadCount(USERNAME), ErrorCode.UNAUTHORIZED);
        assertError(() -> facade.markRead(USERNAME, 31L), ErrorCode.UNAUTHORIZED);
        assertError(() -> facade.markAllRead(USERNAME), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(notificationService);
    }

    private void givenUser(UserRole role) {
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id(USER_ID).username(USERNAME).role(role).build());
    }

    private static List<Notification> notifications(long... ids) {
        return LongStream.of(ids).mapToObj(id -> notification(id, null)).toList();
    }

    // ID가 클수록 최신이 되도록 생성 시각을 ID 분만큼 앞당긴다
    private static Notification notification(long id, LocalDateTime readAt) {
        Notification notification = Notification.create(UUID.randomUUID(), USER_ID,
                NotificationType.PROPOSAL_RECEIVED, "새 제안이 도착했어요", "본문 " + id,
                NotificationTargetType.PROPOSAL, String.valueOf(100 + id));
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "createdAt", CREATED_AT.minusMinutes(id));
        if (readAt != null) {
            notification.markRead(readAt);
        }
        return notification;
    }

    private static void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
