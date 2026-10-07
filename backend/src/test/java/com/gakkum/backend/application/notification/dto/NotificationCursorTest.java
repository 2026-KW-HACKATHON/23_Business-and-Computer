package com.gakkum.backend.application.notification.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.gakkum.backend.application.explore.dto.StoreExploreCursor;
import com.gakkum.backend.application.explore.dto.StoreExploreSort;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("알림 목록 커서")
class NotificationCursorTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 7, 10, 0, 0, 123_456_000);

    @Test
    @DisplayName("생성 시각(마이크로초)과 ID를 Base64URL로 담았다가 그대로 복원한다")
    void roundTrips() {
        String encoded = NotificationCursor.of(CREATED_AT, 42L).encode();

        NotificationCursor decoded = NotificationCursor.decode(encoded);

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(decoded.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(decoded.getId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("원문은 notification-v1|생성 시각|ID 순서로 구성한다")
    void encodesFixedLayout() {
        String encoded = NotificationCursor.of(CREATED_AT, 42L).encode();

        assertThat(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8))
                .isEqualTo("notification-v1|2026-10-07T10:00:00.123456|42");
    }

    @Test
    @DisplayName("매장 탐색 커서는 알림 커서로 해석하지 않는다")
    void rejectsOtherCursor() {
        assertInvalid(StoreExploreCursor.of(StoreExploreSort.LATEST, null, CREATED_AT, 42L).encode());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "notification-v1|2026-10-07T10:00",
            "notification-v1|2026-10-07T10:00|42|extra",
            "notification-v2|2026-10-07T10:00|42",
            "v1|2026-10-07T10:00|42",
            "notification-v1|yesterday|42",
            "notification-v1||42",
            "notification-v1|2026-10-07T10:00|0",
            "notification-v1|2026-10-07T10:00|-1",
            "notification-v1|2026-10-07T10:00|x",
            "notification-v1|2026-10-07T10:00|"})
    @DisplayName("형식·버전·시각·양수 ID가 잘못된 커서는 COMMON_400으로 거부한다")
    void rejectsMalformed(String raw) {
        assertInvalid(encode(raw));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "-4712-01-01T00:00", "+294276-12-31T23:59:59.999999" })
    @DisplayName("PostgreSQL timestamp 범위의 가장 이른 시각과 가장 늦은 시각은 그대로 복원한다")
    void acceptsDatabaseRangeBoundaries(String createdAt) {
        NotificationCursor decoded = NotificationCursor.decode(encode("notification-v1|" + createdAt + "|42"));

        assertThat(decoded.getCreatedAt()).isEqualTo(LocalDateTime.parse(createdAt));
        assertThat(NotificationCursor.decode(decoded.encode()).getCreatedAt()).isEqualTo(decoded.getCreatedAt());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "-4713-12-31T23:59:59.999999999",
            "+294276-12-31T23:59:59.999999001",
            "+294277-01-01T00:00",
            "+999999999-01-01T00:00",
            "-999999999-01-01T00:00"})
    @DisplayName("PostgreSQL timestamp 범위를 벗어난 시각의 커서는 Java가 해석할 수 있어도 COMMON_400으로 거부한다")
    void rejectsCreatedAtOutsideDatabaseRange(String createdAt) {
        // 형식 오류가 아니라 범위 때문에 거부되는 입력이다
        assertThat(LocalDateTime.parse(createdAt)).isNotNull();

        assertInvalid(encode("notification-v1|" + createdAt + "|42"));
    }

    @Test
    @DisplayName("Base64URL이 아닌 문자열은 COMMON_400으로 거부한다")
    void rejectsNonBase64() {
        assertInvalid("!!!");
    }

    private static String encode(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private void assertInvalid(String cursor) {
        assertThatThrownBy(() -> NotificationCursor.decode(cursor))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }
}
