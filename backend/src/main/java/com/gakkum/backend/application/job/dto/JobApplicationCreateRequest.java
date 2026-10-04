package com.gakkum.backend.application.job.dto;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobApplicationCommand;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 의뢰 지원 요청. 마감 준수·페널티 확인 동의는 검증만 하고 저장하지 않는다. */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobApplicationCreateRequest {

    @NotBlank
    @Size(max = 255)
    private String summary;

    @NotBlank
    @Size(max = 500)
    private String workPlan;

    @NotBlank
    @Size(max = 500)
    private String deliveryMethod;

    @NotNull
    @AssertTrue
    private Boolean deadlineAndPenaltyAgreed;

    public static JobApplicationCreateRequest of(
            String summary, String workPlan, String deliveryMethod, Boolean deadlineAndPenaltyAgreed) {
        return JobApplicationCreateRequest.builder()
                .summary(summary)
                .workPlan(workPlan)
                .deliveryMethod(deliveryMethod)
                .deadlineAndPenaltyAgreed(deadlineAndPenaltyAgreed)
                .build();
    }

    public CreateJobApplicationCommand toCommand(String username, Long jobId) {
        return CreateJobApplicationCommand.of(
                username, jobId, summary.trim(), workPlan.trim(), deliveryMethod.trim());
    }
}
