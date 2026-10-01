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
 * 매장 탐색 목록의 다음 페이지 위치. 이전 페이지 마지막 매장의 정렬 키와 그 페이지에 적용한 조건(정렬·업종)을 담는다.
 * 클라이언트에는 해석하지 않는 불투명 문자열(base64url)로 내려준다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class StoreExploreCursor {

    // 제안·의뢰 탐색 커서(v1)와 섞어 쓰지 못하도록 매장 전용 식별자를 쓴다
    private static final String VERSION = "store-v1";
    private static final String DELIMITER = "|";
    private static final String EMPTY = "-";
    private static final int PART_COUNT = 5;

    private final StoreExploreSort sort;
    private final Long businessCategoryId;
    private final LocalDateTime createdAt;
    private final Long id;

    public static StoreExploreCursor of(StoreExploreSort sort, Long businessCategoryId, LocalDateTime createdAt,
            Long id) {
        return new StoreExploreCursor(sort, businessCategoryId, createdAt, id);
    }

    public String encode() {
        String raw = String.join(DELIMITER,
                VERSION,
                sort.name(),
                businessCategoryId == null ? EMPTY : businessCategoryId.toString(),
                createdAt.toString(),
                id.toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 형식이 맞지 않는 커서는 COMMON_400으로 거부한다. */
    public static StoreExploreCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\" + DELIMITER, -1);
            if (parts.length != PART_COUNT || !VERSION.equals(parts[0])) {
                throw invalid();
            }
            StoreExploreSort sort = StoreExploreSort.valueOf(parts[1]);
            Long businessCategoryId = EMPTY.equals(parts[2]) ? null : Long.valueOf(parts[2]);
            LocalDateTime createdAt = LocalDateTime.parse(parts[3]);
            Long id = Long.valueOf(parts[4]);

            if ((businessCategoryId != null && businessCategoryId <= 0) || id <= 0) {
                throw invalid();
            }
            return new StoreExploreCursor(sort, businessCategoryId, createdAt, id);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalid();
        }
    }

    /** 커서를 만든 요청과 같은 조건으로만 다음 페이지를 조회할 수 있다. */
    public boolean matches(StoreExploreSort sort, Long businessCategoryId) {
        return this.sort == sort && Objects.equals(this.businessCategoryId, businessCategoryId);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
}
