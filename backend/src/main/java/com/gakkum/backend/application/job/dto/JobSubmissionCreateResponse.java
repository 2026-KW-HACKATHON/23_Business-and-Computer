package com.gakkum.backend.application.job.dto;

import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionCreateResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobSubmissionCreateResponse {

    private final Long submissionId;
    private final Long jobId;
    private final String submissionType;
    private final Integer revisionNumber;
    private final String reviewStatus;

    public static JobSubmissionCreateResponse from(JobSubmissionCreateResult result) {
        return JobSubmissionCreateResponse.builder()
                .submissionId(result.getSubmissionId())
                .jobId(result.getJobId())
                .submissionType(result.getSubmissionType())
                .revisionNumber(result.getRevisionNumber())
                .reviewStatus(result.getReviewStatus())
                .build();
    }
}
