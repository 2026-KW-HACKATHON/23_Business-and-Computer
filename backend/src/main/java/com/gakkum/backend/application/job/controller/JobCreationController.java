package com.gakkum.backend.application.job.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.job.dto.JobCreateRequest;
import com.gakkum.backend.application.job.facade.JobCreationFacade;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class JobCreationController {

    private final JobCreationFacade jobCreationFacade;

    @PostMapping("/jobs")
    public ApiResponse<Void> createJob(Authentication authentication, @Valid @RequestBody JobCreateRequest request) {
        jobCreationFacade.createJob(authentication.getName(), request.toCommand());
        return ApiResponse.success();
    }
}
