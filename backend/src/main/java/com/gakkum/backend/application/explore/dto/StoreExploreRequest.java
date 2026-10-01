package com.gakkum.backend.application.explore.dto;

import com.gakkum.backend.application.explore.dto.ExploreCommandDto.StoreExploreCommand;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** GET /explore/stores 쿼리 파라미터. 기본값은 컨트롤러에서 채운다. */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StoreExploreRequest {

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private final StoreExploreSort sort;
    private final Long businessCategoryId;
    private final Integer size;
    private final String cursor;

    public static StoreExploreRequest of(StoreExploreSort sort, Long businessCategoryId, Integer size,
            String cursor) {
        return StoreExploreRequest.builder()
                .sort(sort)
                .businessCategoryId(businessCategoryId)
                .size(size)
                .cursor(cursor)
                .build();
    }

    /**
     * 업종 ID는 양수, 크기는 1~100이다.
     * 커서는 해석할 수 있고 이 요청과 같은 조건으로 만든 것이어야 한다. 빈 커서는 첫 페이지로 본다.
     */
    public StoreExploreCommand toCommand(String username) {
        if ((businessCategoryId != null && businessCategoryId <= 0)
                || size == null || size < MIN_SIZE || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        StoreExploreCursor decoded = null;
        if (cursor != null && !cursor.isBlank()) {
            decoded = StoreExploreCursor.decode(cursor);
            if (!decoded.matches(sort, businessCategoryId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
        return StoreExploreCommand.of(username, sort, businessCategoryId, size, decoded);
    }
}
