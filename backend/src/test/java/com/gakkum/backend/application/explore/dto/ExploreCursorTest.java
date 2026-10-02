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

@DisplayName("탐색 커서")
class ExploreCursorTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 30, 10, 0, 0, 123_456_000);

    @Test
    @DisplayName("최신순 커서는 필터와 마지막 카드 위치를 마이크로초까지 그대로 복원한다")
    void roundTripsLatestCursor() {
        String encoded = ExploreCursor.of(ExploreSort.LATEST, ExploreType.ALL, 3L,
                ExploreItemType.JOB, 9, CREATED_AT, 42L).encode();

        ExploreCursor decoded = ExploreCursor.decode(encoded);

        assertThat(encoded).doesNotContain("=", "+", "/");
        assertThat(decoded.getSort()).isEqualTo(ExploreSort.LATEST);
        assertThat(decoded.getType()).isEqualTo(ExploreType.ALL);
        assertThat(decoded.getSpecialtyCategoryId()).isEqualTo(3L);
        assertThat(decoded.getItemType()).isEqualTo(ExploreItemType.JOB);
        assertThat(decoded.getLikeCount()).isNull();
        assertThat(decoded.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(decoded.getId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("좋아요순 커서는 좋아요 수를 담고 분류 없는 필터를 그대로 복원한다")
    void roundTripsLikesCursor() {
        ExploreCursor decoded = ExploreCursor.decode(ExploreCursor.of(ExploreSort.LIKES, ExploreType.PROPOSAL, null,
                ExploreItemType.PROPOSAL, 0, CREATED_AT, 7L).encode());

        assertThat(decoded.getLikeCount()).isZero();
        assertThat(decoded.getSpecialtyCategoryId()).isNull();
        assertThat(decoded.matches(ExploreSort.LIKES, ExploreType.PROPOSAL, null)).isTrue();
    }

    @Test
    @DisplayName("정렬·종류·대분류 중 하나라도 다르면 같은 필터의 커서로 보지 않는다")
    void matchesOnlySameFilter() {
        ExploreCursor cursor = ExploreCursor.of(ExploreSort.LATEST, ExploreType.ALL, 3L,
                ExploreItemType.PROPOSAL, null, CREATED_AT, 1L);

        assertThat(cursor.matches(ExploreSort.LATEST, ExploreType.ALL, 3L)).isTrue();
        assertThat(cursor.matches(ExploreSort.OLDEST, ExploreType.ALL, 3L)).isFalse();
        assertThat(cursor.matches(ExploreSort.LATEST, ExploreType.JOB, 3L)).isFalse();
        assertThat(cursor.matches(ExploreSort.LATEST, ExploreType.ALL, 4L)).isFalse();
        assertThat(cursor.matches(ExploreSort.LATEST, ExploreType.ALL, null)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "not-base64!",
            "v2|LATEST|ALL|-|JOB|-|2026-09-30T10:00|1",
            "v1|LATEST|ALL|-|JOB|-|2026-09-30T10:00",
            "v1|NEWEST|ALL|-|JOB|-|2026-09-30T10:00|1",
            "v1|LATEST|ALL|0|JOB|-|2026-09-30T10:00|1",
            "v1|LATEST|ALL|-|JOB|-|yesterday|1",
            "v1|LATEST|ALL|-|JOB|-|2026-09-30T10:00|0",
            "v1|LATEST|ALL|-|JOB|3|2026-09-30T10:00|1",
            "v1|LATEST|PROPOSAL|-|JOB|-|2026-09-30T10:00|1",
            "v1|LIKES|PROPOSAL|-|PROPOSAL|-|2026-09-30T10:00|1",
            "v1|LIKES|PROPOSAL|-|PROPOSAL|-1|2026-09-30T10:00|1",
            "v1|LIKES|ALL|-|PROPOSAL|3|2026-09-30T10:00|1"})
    @DisplayName("해석할 수 없거나 값끼리 모순되는 커서는 COMMON_400으로 거부한다")
    void rejectsInvalidCursor(String raw) {
        String value = raw.startsWith("v")
                ? Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8))
                : raw;

        assertThatThrownBy(() -> ExploreCursor.decode(value))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }
}
