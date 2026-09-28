package com.gakkum.backend.domain.review.dto;

import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;

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

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentReviewResult {

        private final Long submissionId;
        private final String jobTitle;
        private final String storeName;
        private final Integer rating;
        private final LocalDate createdAt;
        private final List<ReviewPositivePoint> positivePoints;
        private final String content;

        /** 작성일은 서버 로컬 시각 기준 날짜만 내린다. */
        public static StudentReviewResult of(Review review, Long submissionId, String jobTitle, String storeName) {
            return StudentReviewResult.builder()
                    .submissionId(submissionId)
                    .jobTitle(jobTitle)
                    .storeName(storeName)
                    .rating(review.getRating())
                    .createdAt(review.getCreatedAt() == null ? null : review.getCreatedAt().toLocalDate())
                    .positivePoints(review.getPositivePoints())
                    .content(review.getContent())
                    .build();
        }
    }
}
