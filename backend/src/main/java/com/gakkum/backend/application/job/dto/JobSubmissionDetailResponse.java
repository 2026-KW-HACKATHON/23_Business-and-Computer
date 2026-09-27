package com.gakkum.backend.application.job.dto;

import java.util.List;

import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionDetailResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobSubmissionDetailResponse {

    private final Long submissionId;
    private final String title;
    private final String studentName;
    private final String submissionType;
    private final List<String> fileUrls;
    private final String message;
    private final Integer revisionNumber;

    public static JobSubmissionDetailResponse from(JobSubmissionDetailResult result) {
        return JobSubmissionDetailResponse.builder()
                .submissionId(result.getSubmissionId())
                .title(result.getTitle())
                .studentName(result.getStudentName())
                .submissionType(result.getSubmissionType())
                .fileUrls(result.getFileUrls())
                .message(result.getMessage())
                .revisionNumber(result.getRevisionNumber())
                .build();
    }
}
