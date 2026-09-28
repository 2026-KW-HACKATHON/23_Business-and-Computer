package com.gakkum.backend.application.review.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.review.dto.ReviewCreateRequest;
import com.gakkum.backend.application.review.dto.ReviewCreateResponse;
import com.gakkum.backend.application.review.facade.ReviewFacade;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewFacade reviewFacade;

    /** 완료된 의뢰의 사장님이 담당 학생에게 리뷰를 한 번 작성하는 API */
    @PostMapping("/jobs/{jobId}/reviews")
    public ResponseEntity<ApiResponse<ReviewCreateResponse>> createReview(
            Authentication authentication, @PathVariable Long jobId,
            @Valid @RequestBody ReviewCreateRequest request) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ReviewCreateResponse response = ReviewCreateResponse.from(
                reviewFacade.createReview(request.toCommand(authentication.getName(), jobId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
