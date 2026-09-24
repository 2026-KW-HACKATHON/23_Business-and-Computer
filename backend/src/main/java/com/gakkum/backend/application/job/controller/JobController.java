package com.gakkum.backend.application.job.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.job.dto.JobCreateRequest;
import com.gakkum.backend.application.job.facade.JobFacade;
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
}
