package com.gakkum.backend.application.job.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.job.dto.ClosedJobListResponse;
import com.gakkum.backend.application.job.dto.JobCreateRequest;
import com.gakkum.backend.application.job.dto.MatchedJobListResponse;
import com.gakkum.backend.application.job.dto.OpenJobListResponse;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class JobController {

    private final JobFacade jobFacade;

    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<Void>> createJob(Authentication authentication, @Valid @RequestBody JobCreateRequest request) {
        jobFacade.createJob(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/me/jobs")
    public ResponseEntity<ApiResponse<?>> getJobs(
            Authentication authentication, @RequestParam(required = false) String status) {
        if ("OPEN".equals(status)) {
            OpenJobListResponse response = OpenJobListResponse.from(jobFacade.getOpenJobs(authentication.getName()));
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        if ("MATCHED".equals(status)) {
            MatchedJobListResponse response = MatchedJobListResponse.from(jobFacade.getMatchedJobs(authentication.getName()));
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        if ("CLOSED".equals(status)) {
            ClosedJobListResponse response = ClosedJobListResponse.from(jobFacade.getClosedJobs(authentication.getName()));
            return ResponseEntity.ok(ApiResponse.success(response));
        }

        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
}
