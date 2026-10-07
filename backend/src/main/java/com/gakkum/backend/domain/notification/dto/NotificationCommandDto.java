package com.gakkum.backend.domain.notification.dto;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class NotificationCommandDto {

    private NotificationCommandDto() {
    }

    /**
     * 알림 목록 조회 조건. 커서 값은 이전 페이지 마지막 알림의 정렬 키(생성 시각·ID)이고
     * 첫 페이지면 둘 다 null이다.
     */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetNotificationsCommand {

        private final String username;
        private final int size;
        private final LocalDateTime cursorCreatedAt;
        private final Long cursorId;

        public static GetNotificationsCommand of(String username, int size, LocalDateTime cursorCreatedAt,
                Long cursorId) {
            return GetNotificationsCommand.builder()
                    .username(username)
                    .size(size)
                    .cursorCreatedAt(cursorCreatedAt)
                    .cursorId(cursorId)
                    .build();
        }
    }
}
