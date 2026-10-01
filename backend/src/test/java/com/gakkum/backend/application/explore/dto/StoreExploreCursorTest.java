package com.gakkum.backend.application.explore.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("매장 탐색 커서")
class StoreExploreCursorTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 30, 10, 0, 0, 123_456_000);

    @Test
    @DisplayName("정렬·업종·생성 시각(마이크로초)·ID를 Base64URL로 담았다가 그대로 복원한다")
    void roundTrips() {
        String encoded = StoreExploreCursor.of(StoreExploreSort.OLDEST, 3L, CREATED_AT, 42L).encode();

        StoreExploreCursor decoded = StoreExploreCursor.decode(encoded);

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(decoded.getSort()).isEqualTo(StoreExploreSort.OLDEST);
        assertThat(decoded.getBusinessCategoryId()).isEqualTo(3L);
        assertThat(decoded.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(decoded.getId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("커서를 만든 정렬·업종과 같은 조건일 때만 일치한다")
    void matchesOnlySameCondition() {
        StoreExploreCursor all = StoreExploreCursor.decode(
                StoreExploreCursor.of(StoreExploreSort.LATEST, null, CREATED_AT, 42L).encode());

        assertThat(all.matches(StoreExploreSort.LATEST, null)).isTrue();
        assertThat(all.matches(StoreExploreSort.OLDEST, null)).isFalse();
        assertThat(all.matches(StoreExploreSort.LATEST, 3L)).isFalse();
    }

    @Test
    @DisplayName("제안·의뢰 탐색 커서는 매장 커서로 해석하지 않는다")
    void rejectsExploreCursor() {
        String exploreCursor = ExploreCursor.of(ExploreSort.LATEST, ExploreType.ALL, null,
                ExploreItemType.JOB, null, CREATED_AT, 42L).encode();

        assertInvalid(exploreCursor);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "store-v1|LATEST|-|2026-09-30T10:00",
            "store-v2|LATEST|-|2026-09-30T10:00|42",
            "store-v1|LIKES|-|2026-09-30T10:00|42",
            "store-v1|LATEST|0|2026-09-30T10:00|42",
            "store-v1|LATEST|abc|2026-09-30T10:00|42",
            "store-v1|LATEST|-|yesterday|42",
            "store-v1|LATEST|-|2026-09-30T10:00|0",
            "store-v1|LATEST|-|2026-09-30T10:00|x"})
    @DisplayName("형식이나 값이 잘못된 커서는 COMMON_400으로 거부한다")
    void rejectsMalformed(String raw) {
        assertInvalid(Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Base64URL이 아닌 문자열은 COMMON_400으로 거부한다")
    void rejectsNonBase64() {
        assertInvalid("!!!");
    }

    private void assertInvalid(String cursor) {
        assertThatThrownBy(() -> StoreExploreCursor.decode(cursor))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }
}
