package com.gakkum.backend.application.explore.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class ExploreCommandDto {

    private ExploreCommandDto() {
    }

    /** 검증을 마친 탐색 조건. cursor가 null이면 첫 페이지다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ExploreCommand {

        private final String username;
        private final Long specialtyCategoryId;
        private final ExploreType type;
        private final ExploreSort sort;
        private final int size;
        private final ExploreCursor cursor;

        public static ExploreCommand of(String username, Long specialtyCategoryId, ExploreType type,
                ExploreSort sort, int size, ExploreCursor cursor) {
            return ExploreCommand.builder()
                    .username(username)
                    .specialtyCategoryId(specialtyCategoryId)
                    .type(type)
                    .sort(sort)
                    .size(size)
                    .cursor(cursor)
                    .build();
        }
    }
}
