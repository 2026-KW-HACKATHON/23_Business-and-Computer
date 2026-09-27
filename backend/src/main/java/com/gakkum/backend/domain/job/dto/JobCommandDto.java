package com.gakkum.backend.domain.job.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class JobCommandDto {

    private JobCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetOpenJobsCommand {

        private final Long ownerProfileId;

        public static GetOpenJobsCommand of(Long ownerProfileId) {
            return GetOpenJobsCommand.builder()
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetMatchedJobsCommand {

        private final Long ownerProfileId;

        public static GetMatchedJobsCommand of(Long ownerProfileId) {
            return GetMatchedJobsCommand.builder()
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetStudentMatchedJobsCommand {

        private final Long studentProfileId;

        public static GetStudentMatchedJobsCommand of(Long studentProfileId) {
            return GetStudentMatchedJobsCommand.builder()
                    .studentProfileId(studentProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetClosedJobsCommand {

        private final Long ownerProfileId;

        public static GetClosedJobsCommand of(Long ownerProfileId) {
            return GetClosedJobsCommand.builder()
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetJobSubmissionCommand {

        private final Long jobId;
        private final Long ownerProfileId;

        public static GetJobSubmissionCommand of(Long jobId, Long ownerProfileId) {
            return GetJobSubmissionCommand.builder()
                    .jobId(jobId)
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class RequestJobSubmissionRevisionCommand {

        private final Long jobId;
        private final Long submissionId;
        private final Long ownerProfileId;

        public static RequestJobSubmissionRevisionCommand of(Long jobId, Long submissionId, Long ownerProfileId) {
            return RequestJobSubmissionRevisionCommand.builder()
                    .jobId(jobId)
                    .submissionId(submissionId)
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CompleteJobSubmissionCommand {

        private final Long jobId;
        private final Long submissionId;
        private final Long ownerProfileId;

        public static CompleteJobSubmissionCommand of(Long jobId, Long submissionId, Long ownerProfileId) {
            return CompleteJobSubmissionCommand.builder()
                    .jobId(jobId)
                    .submissionId(submissionId)
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PrepareSubmissionFileUploadCommand {

        private final String username;
        private final Long jobId;
        private final JobSubmissionFileType type;
        private final String fileName;
        private final String contentType;
        private final long size;

        public static PrepareSubmissionFileUploadCommand of(
                String username, Long jobId, JobSubmissionFileType type, String fileName, String contentType,
                long size) {
            return PrepareSubmissionFileUploadCommand.builder()
                    .username(username)
                    .jobId(jobId)
                    .type(type)
                    .fileName(fileName)
                    .contentType(contentType)
                    .size(size)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CreateJobSubmissionCommand {

        private final String username;
        private final Long jobId;
        private final List<String> fileUrls;
        private final String message;

        public static CreateJobSubmissionCommand of(
                String username, Long jobId, List<String> fileUrls, String message) {
            return CreateJobSubmissionCommand.builder()
                    .username(username)
                    .jobId(jobId)
                    .fileUrls(List.copyOf(fileUrls))
                    .message(message)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CreateJobCommand {

        private final Long ownerProfileId;
        private final List<Long> specialtyIds;
        private final String title;
        private final String description;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;

        public static CreateJobCommand of(
                Long ownerProfileId,
                List<Long> specialtyIds,
                String title,
                String description,
                Long budget,
                LocalDate draftDeadline,
                LocalDate finalDeadline,
                Integer revisionCount) {
            return CreateJobCommand.builder()
                    .ownerProfileId(ownerProfileId)
                    .specialtyIds(specialtyIds)
                    .title(title)
                    .description(description)
                    .budget(budget)
                    .draftDeadline(draftDeadline)
                    .finalDeadline(finalDeadline)
                    .revisionCount(revisionCount)
                    .build();
        }
    }
}
