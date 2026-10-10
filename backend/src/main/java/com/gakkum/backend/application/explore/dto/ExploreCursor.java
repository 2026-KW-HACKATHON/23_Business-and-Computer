package com.gakkum.backend.application.explore.dto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 탐색 목록의 다음 페이지 위치. 이전 페이지 마지막 카드의 정렬 키와 그 페이지에 적용한 필터(정렬·종류·대분류)를 담는다.
 * 클라이언트에는 해석하지 않는 불투명 문자열(base64url)로 내려준다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExploreCursor {

    private static final String VERSION = "v1";
    private static final String DELIMITER = "|";
    private static final String EMPTY = "-";
    private static final int PART_COUNT = 8;
    // PostgreSQL timestamp가 지원하는 범위(기원전 4713년~294276년, 마이크로초 정밀도). 범위 밖 시각은 쿼리에 넘기지 않는다
    private static final LocalDateTime MIN_CREATED_AT = LocalDateTime.of(-4712, 1, 1, 0, 0);
    private static final LocalDateTime MAX_CREATED_AT = LocalDateTime.of(294276, 12, 31, 23, 59, 59, 999_999_000);

    private final ExploreSort sort;
    private final ExploreType type;
    private final Long specialtyCategoryId;
    private final ExploreItemType itemType;
    // 좋아요순에서만 사용한다
    private final Integer likeCount;
    private final LocalDateTime createdAt;
    private final Long id;

    public static ExploreCursor of(ExploreSort sort, ExploreType type, Long specialtyCategoryId,
            ExploreItemType itemType, Integer likeCount, LocalDateTime createdAt, Long id) {
        return new ExploreCursor(sort, type, specialtyCategoryId, itemType,
                sort == ExploreSort.LIKES ? likeCount : null, createdAt, id);
    }

    public String encode() {
        String raw = String.join(DELIMITER,
                VERSION,
                sort.name(),
                type.name(),
                specialtyCategoryId == null ? EMPTY : specialtyCategoryId.toString(),
                itemType.name(),
                likeCount == null ? EMPTY : likeCount.toString(),
                createdAt.toString(),
                id.toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 형식이 맞지 않거나 값끼리 모순되는 커서는 COMMON_400으로 거부한다. */
    public static ExploreCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\" + DELIMITER, -1);
            if (parts.length != PART_COUNT || !VERSION.equals(parts[0])) {
                throw invalid();
            }
            ExploreSort sort = ExploreSort.valueOf(parts[1]);
            ExploreType type = ExploreType.valueOf(parts[2]);
            Long specialtyCategoryId = EMPTY.equals(parts[3]) ? null : Long.valueOf(parts[3]);
            ExploreItemType itemType = ExploreItemType.valueOf(parts[4]);
            Integer likeCount = EMPTY.equals(parts[5]) ? null : Integer.valueOf(parts[5]);
            LocalDateTime createdAt = LocalDateTime.parse(parts[6]);
            Long id = Long.valueOf(parts[7]);

            boolean validCategory = specialtyCategoryId == null || specialtyCategoryId > 0;
            boolean validItemType = type == ExploreType.ALL || type.name().equals(itemType.name());
            boolean validLikes = sort == ExploreSort.LIKES
                    ? type == ExploreType.PROPOSAL && likeCount != null && likeCount >= 0
                    : likeCount == null;
            boolean validCreatedAt = !createdAt.isBefore(MIN_CREATED_AT) && !createdAt.isAfter(MAX_CREATED_AT);
            if (!validCategory || !validItemType || !validLikes || !validCreatedAt || id <= 0) {
                throw invalid();
            }
            return new ExploreCursor(sort, type, specialtyCategoryId, itemType, likeCount, createdAt, id);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalid();
        }
    }

    /** 커서를 만든 요청과 같은 필터로만 다음 페이지를 조회할 수 있다. */
    public boolean matches(ExploreSort sort, ExploreType type, Long specialtyCategoryId) {
        return this.sort == sort && this.type == type
                && Objects.equals(this.specialtyCategoryId, specialtyCategoryId);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
}
