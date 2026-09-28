package com.gakkum.backend.domain.review.dto;

import com.gakkum.backend.domain.review.entity.Review;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class ReviewQueryDto {

    private ReviewQueryDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReviewCreateResult {

        private final Long reviewId;
        private final Long jobId;

        public static ReviewCreateResult from(Review review) {
            return ReviewCreateResult.builder()
                    .reviewId(review.getId())
                    .jobId(review.getJobId())
                    .build();
        }
    }
}
