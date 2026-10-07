package com.gakkum.backend.application.notification.dto;

import com.gakkum.backend.domain.notification.dto.NotificationCommandDto.GetNotificationsCommand;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** GET /me/notifications 쿼리 파라미터. 기본값은 컨트롤러에서 채운다. */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationListRequest {

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private final Integer size;
    private final String cursor;

    public static NotificationListRequest of(Integer size, String cursor) {
        return NotificationListRequest.builder()
                .size(size)
                .cursor(cursor)
                .build();
    }

    /** 크기는 1~100이고 커서는 해석할 수 있어야 한다. 빈 커서는 첫 페이지로 본다. */
    public GetNotificationsCommand toCommand(String username) {
        if (size == null || size < MIN_SIZE || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (cursor == null || cursor.isBlank()) {
            return GetNotificationsCommand.of(username, size, null, null);
        }
        NotificationCursor decoded = NotificationCursor.decode(cursor);
        return GetNotificationsCommand.of(username, size, decoded.getCreatedAt(), decoded.getId());
    }
}
