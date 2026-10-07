package com.gakkum.backend.application.job.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedWorkerResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentAppliedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentAppliedJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobResult;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class JobListResponse {

    private JobListResponse() {
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OpenJobList {

        private final List<OpenJob> jobs;

        public static OpenJobList from(OpenJobListResult result) {
            return new OpenJobList(result.getJobs().stream()
                    .map(OpenJob::from)
                    .toList());
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OpenJob {

        private final Long jobId;
        private final String title;
        private final List<SpecialtyCategory> specialtyCategories;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final Integer applicantCount;
        private final JobProgressStage progressStage;

        public static OpenJob from(OpenJobResult result) {
            return OpenJob.builder()
                    .jobId(result.getJobId())
                    .title(result.getTitle())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(SpecialtyCategory::from)
                            .toList())
                    .draftDeadline(result.getDraftDeadline())
                    .finalDeadline(result.getFinalDeadline())
                    .revisionCount(result.getRevisionCount())
                    .applicantCount(result.getApplicantCount())
                    .progressStage(result.getProgressStage())
                    .build();
        }
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ClosedJobList {

        private final List<ClosedJob> jobs;

        public static ClosedJobList from(ClosedJobListResult result) {
            return new ClosedJobList(result.getJobs().stream()
                    .map(ClosedJob::from)
                    .toList());
        }

        @JsonValue
        public List<ClosedJob> getJobs() {
            return jobs;
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ClosedJob {

        private final Long jobId;
        private final String title;
        private final List<ClosedSpecialtyCategory> specialtyCategories;
        private final MatchedWorker matchedWorker;
        private final LocalDate completedAt;
        private final JobProgressStage progressStage;

        public static ClosedJob from(ClosedJobResult result) {
            return ClosedJob.builder()
                    .jobId(result.getJobId())
                    .title(result.getTitle())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(ClosedSpecialtyCategory::from)
                            .toList())
                    .matchedWorker(result.getMatchedWorker() == null ? null : MatchedWorker.from(result.getMatchedWorker()))
                    .completedAt(result.getCompletedAt())
                    .progressStage(result.getProgressStage())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MatchedJobList {

        private final List<MatchedJob> jobs;

        public static MatchedJobList from(MatchedJobListResult result) {
            return new MatchedJobList(result.getJobs().stream()
                    .map(MatchedJob::from)
                    .toList());
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MatchedJob {

        private final Long jobId;
        private final String title;
        private final List<SpecialtyCategory> specialtyCategories;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Long studentProfileId;
        private final String studentNumber;
        private final String major;
        private final String submissionType;
        private final Long pendingSubmissionId;
        private final JobProgressStage progressStage;
        private final String studentName;
        private final Long budget;
        private final Integer revisionCount;
        private final Integer revisionNumber;
        private final LocalDateTime submittedAt;

        public static MatchedJob from(MatchedJobResult result) {
            return MatchedJob.builder()
                    .jobId(result.getJobId())
                    .title(result.getTitle())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(SpecialtyCategory::from)
                            .toList())
                    .draftDeadline(result.getDraftDeadline())
                    .finalDeadline(result.getFinalDeadline())
                    .studentProfileId(result.getStudentProfileId())
                    .studentNumber(result.getStudentNumber())
                    .major(result.getMajor())
                    .submissionType(result.getSubmissionType())
                    .pendingSubmissionId(result.getPendingSubmissionId())
                    .progressStage(result.getProgressStage())
                    .studentName(result.getStudentName())
                    .budget(result.getBudget())
                    .revisionCount(result.getRevisionCount())
                    .revisionNumber(result.getRevisionNumber())
                    .submittedAt(result.getSubmittedAt())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentMatchedJobList {

        private final List<StudentMatchedJob> jobs;

        public static StudentMatchedJobList from(StudentMatchedJobListResult result) {
            return new StudentMatchedJobList(result.getJobs().stream()
                    .map(StudentMatchedJob::from)
                    .toList());
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentMatchedJob {

        private final Long jobId;
        private final String title;
        private final List<SpecialtyCategory> specialtyCategories;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final String submissionType;
        private final String reviewStatus;
        private final JobProgressStage progressStage;
        private final String storeName;
        private final LocalDateTime submittedAt;

        public static StudentMatchedJob from(StudentMatchedJobResult result) {
            return StudentMatchedJob.builder()
                    .jobId(result.getJobId())
                    .title(result.getTitle())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(SpecialtyCategory::from)
                            .toList())
                    .budget(result.getBudget())
                    .draftDeadline(result.getDraftDeadline())
                    .finalDeadline(result.getFinalDeadline())
                    .revisionCount(result.getRevisionCount())
                    .submissionType(result.getSubmissionType())
                    .reviewStatus(result.getReviewStatus())
                    .progressStage(result.getProgressStage())
                    .storeName(result.getStoreName())
                    .submittedAt(result.getSubmittedAt())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentAppliedJobList {

        private final List<StudentAppliedJob> jobs;

        public static StudentAppliedJobList from(StudentAppliedJobListResult result) {
            return new StudentAppliedJobList(result.getJobs().stream()
                    .map(StudentAppliedJob::from)
                    .toList());
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentAppliedJob {

        private final Long jobId;
        private final Long jobApplicationId;
        private final String title;
        private final List<SpecialtyCategory> specialtyCategories;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final JobStatus jobStatus;
        private final JobApplicationStatus applicationStatus;
        private final LocalDateTime appliedAt;
        private final String storeName;
        private final String summary;
        private final String workPlan;
        private final String deliveryMethod;

        public static StudentAppliedJob from(StudentAppliedJobResult result) {
            return StudentAppliedJob.builder()
                    .jobId(result.getJobId())
                    .jobApplicationId(result.getJobApplicationId())
                    .title(result.getTitle())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(SpecialtyCategory::from)
                            .toList())
                    .budget(result.getBudget())
                    .draftDeadline(result.getDraftDeadline())
                    .finalDeadline(result.getFinalDeadline())
                    .jobStatus(result.getJobStatus())
                    .applicationStatus(result.getApplicationStatus())
                    .appliedAt(result.getAppliedAt())
                    .storeName(result.getStoreName())
                    .summary(result.getSummary())
                    .workPlan(result.getWorkPlan())
                    .deliveryMethod(result.getDeliveryMethod())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyCategory {

        private final Long id;
        private final String name;
        private final List<Specialty> specialties;

        public static SpecialtyCategory from(SpecialtyCategoryResult result) {
            return new SpecialtyCategory(
                    result.getId(),
                    result.getName(),
                    result.getSpecialties().stream().map(Specialty::from).toList());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Specialty {

        private final Long id;
        private final String name;

        public static Specialty from(SpecialtyResult result) {
            return new Specialty(result.getId(), result.getName());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ClosedSpecialtyCategory {

        private final Long id;
        private final String name;

        public static ClosedSpecialtyCategory from(SpecialtyCategoryResult result) {
            return new ClosedSpecialtyCategory(result.getId(), result.getName());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MatchedWorker {

        private final Long studentProfileId;
        private final String name;

        public static MatchedWorker from(MatchedWorkerResult result) {
            return new MatchedWorker(result.getStudentProfileId(), result.getName());
        }
    }
}
