package com.gakkum.backend.application.review.dto;

import com.gakkum.backend.domain.review.dto.ReviewQueryDto.ReviewCreateResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReviewCreateResponse {

    private final Long reviewId;
    private final Long jobId;

    public static ReviewCreateResponse from(ReviewCreateResult result) {
        return ReviewCreateResponse.builder()
                .reviewId(result.getReviewId())
                .jobId(result.getJobId())
                .build();
    }
}
