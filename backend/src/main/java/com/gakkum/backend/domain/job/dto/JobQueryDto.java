package com.gakkum.backend.domain.job.dto;

import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.user.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class JobQueryDto {

    private JobQueryDto() {
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OpenJobData {

        private final Job job;
        private final List<Long> specialtyIds;
        private final Integer applicantCount;

        public static OpenJobData of(Job job, List<Long> specialtyIds, Integer applicantCount) {
            return new OpenJobData(job, specialtyIds, applicantCount);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MatchedJobData {

        private final Job job;
        private final List<Long> specialtyIds;
        private final JobSubmission pendingSubmission;

        public static MatchedJobData of(Job job, List<Long> specialtyIds, JobSubmission pendingSubmission) {
            return new MatchedJobData(job, specialtyIds, pendingSubmission);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ClosedJobData {

        private final Job job;
        private final List<Long> specialtyIds;

        public static ClosedJobData of(Job job, List<Long> specialtyIds) {
            return new ClosedJobData(job, specialtyIds);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ClosedJobListResult {

        private final List<ClosedJobResult> jobs;

        public static ClosedJobListResult of(List<ClosedJobResult> jobs) {
            return new ClosedJobListResult(jobs);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ClosedJobResult {

        private final Long jobId;
        private final String title;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final MatchedWorkerResult matchedWorker;
        private final LocalDate completedAt;

        public static ClosedJobResult of(
                ClosedJobData data, Student student, User worker, List<SpecialtyCategoryResult> specialtyCategories) {
            Job job = data.getJob();
            return ClosedJobResult.builder()
                    .jobId(job.getId())
                    .title(job.getTitle())
                    .specialtyCategories(specialtyCategories)
                    .matchedWorker(MatchedWorkerResult.of(student.getId(), worker.getName()))
                    .completedAt(job.getCompletedAt().toLocalDate())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MatchedWorkerResult {

        private final Long studentProfileId;
        private final String name;

        public static MatchedWorkerResult of(Long studentProfileId, String name) {
            return new MatchedWorkerResult(studentProfileId, name);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MatchedJobListResult {

        private final List<MatchedJobResult> jobs;

        public static MatchedJobListResult of(List<MatchedJobResult> jobs) {
            return new MatchedJobListResult(jobs);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MatchedJobResult {

        private final Long jobId;
        private final String title;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Long studentProfileId;
        private final String studentNumber;
        private final String major;
        private final String submissionType;
        private final Long pendingSubmissionId;

        public static MatchedJobResult of(
                MatchedJobData data, Student student, List<SpecialtyCategoryResult> specialtyCategories) {
            Job job = data.getJob();
            JobSubmission pendingSubmission = data.getPendingSubmission();
            return MatchedJobResult.builder()
                    .jobId(job.getId())
                    .title(job.getTitle())
                    .specialtyCategories(specialtyCategories)
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .studentProfileId(student.getId())
                    .studentNumber(student.getStudentNumber())
                    .major(student.getMajor())
                    .submissionType(pendingSubmission == null ? null : pendingSubmission.getSubmissionType().name())
                    .pendingSubmissionId(pendingSubmission == null ? null : pendingSubmission.getId())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OpenJobListResult {

        private final List<OpenJobResult> jobs;

        public static OpenJobListResult of(List<OpenJobResult> jobs) {
            return new OpenJobListResult(jobs);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OpenJobResult {

        private final Long jobId;
        private final String title;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final Integer applicantCount;

        public static OpenJobResult of(OpenJobData data, List<SpecialtyCategoryResult> specialtyCategories) {
            Job job = data.getJob();
            return OpenJobResult.builder()
                    .jobId(job.getId())
                    .title(job.getTitle())
                    .specialtyCategories(specialtyCategories)
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .revisionCount(job.getRevisionCount())
                    .applicantCount(data.getApplicantCount())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyCategoryResult {

        private final Long id;
        private final String name;
        private final List<SpecialtyResult> specialties;

        public static SpecialtyCategoryResult of(Long id, String name, List<SpecialtyResult> specialties) {
            return new SpecialtyCategoryResult(id, name, specialties);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyResult {

        private final Long id;
        private final String name;

        public static SpecialtyResult of(Long id, String name) {
            return new SpecialtyResult(id, name);
        }
    }
}
