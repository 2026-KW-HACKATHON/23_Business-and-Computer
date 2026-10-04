package com.gakkum.backend.application.job.dto;

import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicationCreateResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobApplicationCreateResponse {

    private final Long jobApplicationId;

    public static JobApplicationCreateResponse from(JobApplicationCreateResult result) {
        return JobApplicationCreateResponse.builder()
                .jobApplicationId(result.getJobApplicationId())
                .build();
    }
}
