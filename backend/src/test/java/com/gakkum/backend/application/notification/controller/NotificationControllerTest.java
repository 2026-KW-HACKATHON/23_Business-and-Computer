package com.gakkum.backend.application.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.explore.dto.StoreExploreCursor;
import com.gakkum.backend.application.explore.dto.StoreExploreSort;
import com.gakkum.backend.application.notification.dto.NotificationCursor;
import com.gakkum.backend.application.notification.facade.NotificationFacade;
import com.gakkum.backend.domain.notification.dto.NotificationCommandDto.GetNotificationsCommand;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationListResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationReadAllResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationReadResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationUnreadCountResult;
import com.gakkum.backend.domain.notification.entity.Notification;
import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("알림 컨트롤러 (목록·미읽음 개수·개별 읽음·모두 읽음)")
class NotificationControllerTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String RECIPIENT = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 7, 10, 30, 0, 123_456_000);
    private static final LocalDateTime READ_AT = LocalDateTime.of(2026, 10, 7, 11, 0, 15);

    private final NotificationFacade notificationFacade = mock(NotificationFacade.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("목록은 파라미터가 없으면 20개·첫 페이지로 조회하고 빈 목록과 null 커서를 그대로 반환한다")
    void usesDefaults() throws Exception {
        when(notificationFacade.getNotifications(any())).thenReturn(NotificationListResult.of(List.of(), null));

        mockMvc.perform(get("/me/notifications").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.nextCursor").value((Object) null));

        GetNotificationsCommand command = captureCommand();
        assertThat(command.getUsername()).isEqualTo(USERNAME);
        assertThat(command.getSize()).isEqualTo(20);
        assertThat(command.getCursorCreatedAt()).isNull();
        assertThat(command.getCursorId()).isNull();
    }

    @Test
    @DisplayName("목록은 크기와 커서를 해석해 파사드에 넘긴다")
    void passesSizeAndCursor() throws Exception {
        when(notificationFacade.getNotifications(any())).thenReturn(NotificationListResult.of(List.of(), null));

        mockMvc.perform(get("/me/notifications").principal(authentication)
                        .param("size", "100")
                        .param("cursor", NotificationCursor.of(CREATED_AT, 42L).encode()))
                .andExpect(status().isOk());

        GetNotificationsCommand command = captureCommand();
        assertThat(command.getSize()).isEqualTo(100);
        assertThat(command.getCursorCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(command.getCursorId()).isEqualTo(42L);
    }

    @ParameterizedTest(name = "커서 \"{0}\"")
    @ValueSource(strings = { "", "   " })
    @DisplayName("목록은 비어 있거나 공백인 커서를 첫 페이지로 조회한다")
    void treatsBlankCursorAsFirstPage(String cursor) throws Exception {
        when(notificationFacade.getNotifications(any())).thenReturn(NotificationListResult.of(List.of(), null));

        mockMvc.perform(get("/me/notifications").principal(authentication).param("size", "1").param("cursor", cursor))
                .andExpect(status().isOk());

        GetNotificationsCommand command = captureCommand();
        assertThat(command.getSize()).isEqualTo(1);
        assertThat(command.getCursorCreatedAt()).isNull();
        assertThat(command.getCursorId()).isNull();
    }

    @Test
    @DisplayName("목록 항목은 8개 필드만 담고 이벤트 ID·수신자 ID는 내리지 않으며 미읽음의 읽음 시각은 null이다")
    void returnsItems() throws Exception {
        Notification unread = notification(7L, NotificationType.CHAT_MESSAGE_RECEIVED, "새 메시지가 도착했어요",
                "안녕하세요, 시안 보내드립니다.", NotificationTargetType.CHAT_ROOM, "01K58M6PJV8VAJMXHBHJ2PNB5E", null);
        Notification read = notification(6L, NotificationType.JOB_APPLICATION_RECEIVED, "새 지원자가 있어요",
                "메뉴판 디자인 의뢰에 지원했어요.", NotificationTargetType.JOB, "42", READ_AT);
        when(notificationFacade.getNotifications(any())).thenReturn(NotificationListResult.of(
                List.of(NotificationResult.from(unread), NotificationResult.from(read)), "next"));

        mockMvc.perform(get("/me/notifications").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].length()").value(8))
                .andExpect(jsonPath("$.data.items[0].id").value(7))
                .andExpect(jsonPath("$.data.items[0].type").value("CHAT_MESSAGE_RECEIVED"))
                .andExpect(jsonPath("$.data.items[0].title").value("새 메시지가 도착했어요"))
                .andExpect(jsonPath("$.data.items[0].body").value("안녕하세요, 시안 보내드립니다."))
                .andExpect(jsonPath("$.data.items[0].targetType").value("CHAT_ROOM"))
                .andExpect(jsonPath("$.data.items[0].targetId").value("01K58M6PJV8VAJMXHBHJ2PNB5E"))
                .andExpect(jsonPath("$.data.items[0].readAt").value((Object) null))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-10-07T19:30:00.123456+09:00"))
                .andExpect(jsonPath("$.data.items[0].eventId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].recipientUserId").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].length()").value(8))
                .andExpect(jsonPath("$.data.items[1].id").value(6))
                .andExpect(jsonPath("$.data.items[1].targetType").value("JOB"))
                .andExpect(jsonPath("$.data.items[1].targetId").value("42"))
                .andExpect(jsonPath("$.data.items[1].readAt").value("2026-10-07T20:00:15+09:00"))
                .andExpect(jsonPath("$.data.nextCursor").value("next"));
    }

    static Stream<Arguments> invalidListRequests() {
        String storeCursor = StoreExploreCursor.of(StoreExploreSort.LATEST, null, CREATED_AT, 1L).encode();
        String outOfRangeCursor = NotificationCursor.of(LocalDateTime.of(999_999_999, 1, 1, 0, 0), 1L).encode();
        return Stream.of(
                Arguments.of("크기 0", get("/me/notifications").param("size", "0")),
                Arguments.of("음수 크기", get("/me/notifications").param("size", "-1")),
                Arguments.of("크기 101", get("/me/notifications").param("size", "101")),
                Arguments.of("숫자가 아닌 크기", get("/me/notifications").param("size", "many")),
                Arguments.of("해석할 수 없는 커서", get("/me/notifications").param("cursor", "abc")),
                Arguments.of("매장 탐색 커서", get("/me/notifications").param("cursor", storeCursor)),
                Arguments.of("DB 범위를 벗어난 시각의 커서", get("/me/notifications").param("cursor", outOfRangeCursor)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidListRequests")
    @DisplayName("목록은 범위를 벗어난 크기와 잘못되었거나 DB 범위 밖 시각인 커서를 COMMON_400으로 거부하고 파사드를 호출하지 않는다")
    void rejectsInvalidListRequest(String caseName, MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(notificationFacade);
    }

    @Test
    @DisplayName("미읽음 개수는 인증 사용자의 개수만 반환한다")
    void returnsUnreadCount() throws Exception {
        when(notificationFacade.getUnreadCount(USERNAME)).thenReturn(NotificationUnreadCountResult.of(3L));

        mockMvc.perform(get("/me/notifications/unread-count").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data.unreadCount").value(3));

        verify(notificationFacade).getUnreadCount(USERNAME);
    }

    @Test
    @DisplayName("개별 읽음은 본문 없이 인증 사용자와 알림 ID를 전달하고 알림 ID와 읽음 시각만 반환한다")
    void marksRead() throws Exception {
        Notification read = notification(31L, NotificationType.PAYMENT_COMPLETED, "결제가 완료됐어요", "본문",
                NotificationTargetType.PAYMENT, "9", READ_AT);
        when(notificationFacade.markRead(USERNAME, 31L)).thenReturn(NotificationReadResult.from(read));

        mockMvc.perform(put("/me/notifications/31/read").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data.notificationId").value(31))
                .andExpect(jsonPath("$.data.readAt").value("2026-10-07T20:00:15+09:00"));

        verify(notificationFacade).markRead(USERNAME, 31L);
    }

    @ParameterizedTest(name = "알림 ID \"{0}\"")
    @ValueSource(strings = { "0", "-1", "abc", "1.5" })
    @DisplayName("개별 읽음은 숫자가 아니거나 양수가 아닌 알림 ID를 COMMON_400으로 거부하고 파사드를 호출하지 않는다")
    void rejectsInvalidNotificationId(String notificationId) throws Exception {
        mockMvc.perform(put("/me/notifications/" + notificationId + "/read").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(notificationFacade);
    }

    @Test
    @DisplayName("개별 읽음은 없거나 다른 사용자의 알림을 NOTIFICATION_404와 같은 메시지로 응답한다")
    void returnsNotFound() throws Exception {
        when(notificationFacade.markRead(USERNAME, 31L))
                .thenThrow(new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        mockMvc.perform(put("/me/notifications/31/read").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_404"))
                .andExpect(jsonPath("$.error.message").value("알림을 찾을 수 없습니다."));
    }

    @ParameterizedTest(name = "변경 수 {0}")
    @ValueSource(ints = { 5, 0 })
    @DisplayName("모두 읽음은 본문 없이 인증 사용자를 전달하고 대상이 없어도 변경 수만 담아 200으로 응답한다")
    void marksAllRead(int updatedCount) throws Exception {
        when(notificationFacade.markAllRead(USERNAME)).thenReturn(NotificationReadAllResult.of(updatedCount));

        mockMvc.perform(put("/me/notifications/read").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data.updatedCount").value(updatedCount));

        verify(notificationFacade).markAllRead(USERNAME);
    }

    @Test
    @DisplayName("잠긴 사용자 등 사용자 확인 실패는 네 API 모두 COMMON_401로 응답한다")
    void returnsUnauthorizedForInactiveUser() throws Exception {
        BusinessException unauthorized = new BusinessException(ErrorCode.UNAUTHORIZED);
        when(notificationFacade.getNotifications(any())).thenThrow(unauthorized);
        when(notificationFacade.getUnreadCount(USERNAME)).thenThrow(unauthorized);
        when(notificationFacade.markRead(USERNAME, 31L)).thenThrow(unauthorized);
        when(notificationFacade.markAllRead(USERNAME)).thenThrow(unauthorized);

        for (MockHttpServletRequestBuilder request : List.of(
                get("/me/notifications"), get("/me/notifications/unread-count"),
                put("/me/notifications/31/read"), put("/me/notifications/read"))) {
            mockMvc.perform(request.principal(authentication))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        }
    }

    private GetNotificationsCommand captureCommand() {
        ArgumentCaptor<GetNotificationsCommand> captor = ArgumentCaptor.forClass(GetNotificationsCommand.class);
        verify(notificationFacade).getNotifications(captor.capture());
        return captor.getValue();
    }

    private static Notification notification(Long id, NotificationType type, String title, String body,
            NotificationTargetType targetType, String targetId, LocalDateTime readAt) {
        Notification notification = Notification.create(UUID.randomUUID(), RECIPIENT, type, title, body,
                targetType, targetId);
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "createdAt", CREATED_AT);
        if (readAt != null) {
            notification.markRead(readAt);
        }
        return notification;
    }
}
