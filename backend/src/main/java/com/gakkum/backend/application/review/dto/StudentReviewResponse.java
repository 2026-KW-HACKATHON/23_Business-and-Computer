package com.gakkum.backend.application.review.dto;

import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.domain.review.dto.ReviewQueryDto.StudentReviewResult;
import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentReviewResponse {

    private final Long submissionId;
    private final String jobTitle;
    private final String storeName;
    private final Integer rating;
    private final LocalDate createdAt;
    private final List<ReviewPositivePoint> positivePoints;
    private final String content;

    public static StudentReviewResponse from(StudentReviewResult result) {
        return StudentReviewResponse.builder()
                .submissionId(result.getSubmissionId())
                .jobTitle(result.getJobTitle())
                .storeName(result.getStoreName())
                .rating(result.getRating())
                .createdAt(result.getCreatedAt())
                .positivePoints(result.getPositivePoints())
                .content(result.getContent())
                .build();
    }
}
