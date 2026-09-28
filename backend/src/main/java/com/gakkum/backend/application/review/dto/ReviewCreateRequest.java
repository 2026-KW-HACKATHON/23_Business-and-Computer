package com.gakkum.backend.application.review.dto;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

import com.gakkum.backend.domain.review.dto.ReviewCommandDto.CreateReviewCommand;
import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사장님 → 학생 리뷰 작성 요청. 의뢰 ID는 경로로만 받는다. */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReviewCreateRequest {

    // 생략하거나 null이면 빈 목록으로 저장한다
    @Size(max = 5)
    private List<@NotNull ReviewPositivePoint> positivePoints;

    @NotBlank
    @Size(max = 5000)
    private String content;

    // Integer로 받으면 3.5가 3으로 잘려 저장되므로 소수를 받은 뒤 정수만 허용한다
    @NotNull
    @Digits(integer = 1, fraction = 0)
    @DecimalMin("1")
    @DecimalMax("5")
    private BigDecimal rating;

    public static ReviewCreateRequest of(List<ReviewPositivePoint> positivePoints, String content, Integer rating) {
        return ReviewCreateRequest.builder()
                .positivePoints(positivePoints)
                .content(content)
                .rating(rating == null ? null : BigDecimal.valueOf(rating))
                .build();
    }

    @AssertTrue
    private boolean isPositivePointsDistinct() {
        return positivePoints == null || new HashSet<>(positivePoints).size() == positivePoints.size();
    }

    public CreateReviewCommand toCommand(String username, Long jobId) {
        return CreateReviewCommand.of(username, jobId, positivePoints, content.trim(), rating.intValueExact());
    }
}
