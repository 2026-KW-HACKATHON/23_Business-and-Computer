package com.gakkum.backend.application.job.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionCreateResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionDetailResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.PrepareSubmissionFileUploadResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class JobSubmissionResponse {

    private JobSubmissionResponse() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Create {

        private final Long submissionId;
        private final Long jobId;
        private final String submissionType;
        private final Integer revisionNumber;
        private final String reviewStatus;

        public static Create from(JobSubmissionCreateResult result) {
            return Create.builder()
                    .submissionId(result.getSubmissionId())
                    .jobId(result.getJobId())
                    .submissionType(result.getSubmissionType())
                    .revisionNumber(result.getRevisionNumber())
                    .reviewStatus(result.getReviewStatus())
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Detail {

        private final Long submissionId;
        private final String title;
        private final String studentName;
        private final String submissionType;
        private final List<String> fileUrls;
        private final String message;
        private final Integer revisionNumber;

        public static Detail from(JobSubmissionDetailResult result) {
            return Detail.builder()
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

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PrepareFileUpload {

        private final String uploadUrl;
        private final Map<String, String> uploadHeaders;
        private final LocalDateTime uploadUrlExpiresAt;
        private final String fileUrl;

        public static PrepareFileUpload from(PrepareSubmissionFileUploadResult result) {
            return PrepareFileUpload.builder()
                    .uploadUrl(result.getUploadUrl())
                    .uploadHeaders(result.getUploadHeaders())
                    .uploadUrlExpiresAt(result.getUploadUrlExpiresAt())
                    .fileUrl(result.getFileUrl())
                    .build();
        }
    }
}
