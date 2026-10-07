package com.gakkum.backend.application.notification.dto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 알림 목록의 다음 페이지 위치. 이전 페이지 마지막 알림의 정렬 키(생성 시각·ID)를 담는다.
 * 클라이언트에는 해석하지 않는 불투명 문자열(base64url)로 내려준다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotificationCursor {

    // 탐색 커서(v1, store-v1)와 섞어 쓰지 못하도록 알림 전용 식별자를 쓴다
    private static final String VERSION = "notification-v1";
    private static final String DELIMITER = "|";
    private static final int PART_COUNT = 3;
    // PostgreSQL timestamp가 지원하는 범위(기원전 4713년~294276년, 마이크로초 정밀도). 범위 밖 시각은 쿼리에 넘기지 않는다
    private static final LocalDateTime MIN_CREATED_AT = LocalDateTime.of(-4712, 1, 1, 0, 0);
    private static final LocalDateTime MAX_CREATED_AT = LocalDateTime.of(294276, 12, 31, 23, 59, 59, 999_999_000);

    private final LocalDateTime createdAt;
    private final Long id;

    public static NotificationCursor of(LocalDateTime createdAt, Long id) {
        return new NotificationCursor(createdAt, id);
    }

    public String encode() {
        String raw = String.join(DELIMITER, VERSION, createdAt.toString(), id.toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 형식이 맞지 않거나 DB가 다룰 수 없는 시각을 담은 커서는 COMMON_400으로 거부한다. */
    public static NotificationCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\" + DELIMITER, -1);
            if (parts.length != PART_COUNT || !VERSION.equals(parts[0])) {
                throw invalid();
            }
            LocalDateTime createdAt = LocalDateTime.parse(parts[1]);
            Long id = Long.valueOf(parts[2]);

            if (id <= 0 || createdAt.isBefore(MIN_CREATED_AT) || createdAt.isAfter(MAX_CREATED_AT)) {
                throw invalid();
            }
            return new NotificationCursor(createdAt, id);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
}
