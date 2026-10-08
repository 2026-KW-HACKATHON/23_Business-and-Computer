package com.gakkum.backend.application.job.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.gakkum.backend.domain.job.dto.JobQueryDto.JobLatestSubmissionResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionCreateResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionDetailResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionHistoryResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.PrepareSubmissionFileUploadResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.RevisionRequestResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SubmissionFileResult;
import com.gakkum.backend.global.response.KoreaTime;

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
        private final List<File> files;
        private final String message;
        private final Integer revisionNumber;

        public static Detail from(JobSubmissionDetailResult result) {
            return Detail.builder()
                    .submissionId(result.getSubmissionId())
                    .title(result.getTitle())
                    .studentName(result.getStudentName())
                    .submissionType(result.getSubmissionType())
                    .fileUrls(result.getFileUrls())
                    .files(File.listFrom(result.getFiles()))
                    .message(result.getMessage())
                    .revisionNumber(result.getRevisionNumber())
                    .build();
        }
    }

    /** 수정 요청을 받지 않은 제출물은 revisionRequest를 생략하지 않고 null로 내린다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Latest {

        private final Long submissionId;
        private final String submissionType;
        private final Integer revisionNumber;
        private final List<String> fileUrls;
        private final List<File> files;
        private final String message;
        private final String reviewStatus;
        private final OffsetDateTime submittedAt;
        private final RevisionRequest revisionRequest;

        public static Latest from(JobLatestSubmissionResult result) {
            return Latest.builder()
                    .submissionId(result.getSubmissionId())
                    .submissionType(result.getSubmissionType())
                    .revisionNumber(result.getRevisionNumber())
                    .fileUrls(result.getFileUrls())
                    .files(File.listFrom(result.getFiles()))
                    .message(result.getMessage())
                    .reviewStatus(result.getReviewStatus())
                    .submittedAt(KoreaTime.from(result.getSubmittedAt()))
                    .revisionRequest(result.getRevisionRequest() == null
                            ? null
                            : RevisionRequest.from(result.getRevisionRequest()))
                    .build();
        }
    }

    /** 제출물이 없는 의뢰는 submissions를 빈 배열로 내린다. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class History {

        private final List<Latest> submissions;

        public static History from(JobSubmissionHistoryResult result) {
            return new History(result.getSubmissions().stream()
                    .map(Latest::from)
                    .toList());
        }
    }

    /** 크기를 기록하기 전에 제출된 파일은 size를 생략하지 않고 null로 내린다. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class File {

        private final String fileUrl;
        private final Long size;

        public static List<File> listFrom(List<SubmissionFileResult> results) {
            return results.stream()
                    .map(result -> new File(result.getFileUrl(), result.getSize()))
                    .toList();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class RevisionRequest {

        private final String message;
        private final List<String> referenceImageUrls;
        private final OffsetDateTime requestedAt;

        public static RevisionRequest from(RevisionRequestResult result) {
            return RevisionRequest.builder()
                    .message(result.getMessage())
                    .referenceImageUrls(result.getReferenceImageUrls())
                    .requestedAt(KoreaTime.from(result.getRequestedAt()))
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PrepareFileUpload {

        private final String uploadUrl;
        private final Map<String, String> uploadHeaders;
        private final OffsetDateTime uploadUrlExpiresAt;
        private final String fileUrl;

        public static PrepareFileUpload from(PrepareSubmissionFileUploadResult result) {
            return PrepareFileUpload.builder()
                    .uploadUrl(result.getUploadUrl())
                    .uploadHeaders(result.getUploadHeaders())
                    .uploadUrlExpiresAt(KoreaTime.from(result.getUploadUrlExpiresAt()))
                    .fileUrl(result.getFileUrl())
                    .build();
        }
    }
}
