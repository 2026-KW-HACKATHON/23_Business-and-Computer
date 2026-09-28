package com.gakkum.backend.domain.review.dto;

import java.util.List;

import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class ReviewCommandDto {

    private ReviewCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CreateReviewCommand {

        private final String username;
        private final Long jobId;
        private final List<ReviewPositivePoint> positivePoints;
        private final String content;
        private final Integer rating;

        /** 좋은 점을 생략하면 빈 목록으로 받는다. */
        public static CreateReviewCommand of(
                String username, Long jobId, List<ReviewPositivePoint> positivePoints, String content,
                Integer rating) {
            return CreateReviewCommand.builder()
                    .username(username)
                    .jobId(jobId)
                    .positivePoints(positivePoints == null ? List.of() : List.copyOf(positivePoints))
                    .content(content)
                    .rating(rating)
                    .build();
        }
    }
}
