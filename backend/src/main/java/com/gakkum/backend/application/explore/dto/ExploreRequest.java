package com.gakkum.backend.application.explore.dto;

import com.gakkum.backend.application.explore.dto.ExploreCommandDto.ExploreCommand;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** GET /explore 쿼리 파라미터. 기본값은 컨트롤러에서 채운다. */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExploreRequest {

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private final Long specialtyCategoryId;
    private final ExploreType type;
    private final ExploreSort sort;
    private final Integer size;
    private final String cursor;

    public static ExploreRequest of(Long specialtyCategoryId, ExploreType type, ExploreSort sort, Integer size,
            String cursor) {
        return ExploreRequest.builder()
                .specialtyCategoryId(specialtyCategoryId)
                .type(type)
                .sort(sort)
                .size(size)
                .cursor(cursor)
                .build();
    }

    /**
     * 분류 ID는 양수, 크기는 1~100, 좋아요순은 제안 전용이다.
     * 커서는 해석할 수 있고 이 요청과 같은 필터로 만든 것이어야 한다. 빈 커서는 첫 페이지로 본다.
     */
    public ExploreCommand toCommand(String username) {
        if ((specialtyCategoryId != null && specialtyCategoryId <= 0)
                || size == null || size < MIN_SIZE || size > MAX_SIZE
                || (sort == ExploreSort.LIKES && type != ExploreType.PROPOSAL)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        ExploreCursor decoded = null;
        if (cursor != null && !cursor.isBlank()) {
            decoded = ExploreCursor.decode(cursor);
            if (!decoded.matches(sort, type, specialtyCategoryId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
        return ExploreCommand.of(username, specialtyCategoryId, type, sort, size, decoded);
    }
}
