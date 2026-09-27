package com.gakkum.backend.domain.job.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
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
    public static class JobDetailData {

        private final Job job;
        private final List<Long> specialtyIds;

        public static JobDetailData of(Job job, List<Long> specialtyIds) {
            return new JobDetailData(job, specialtyIds);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobDetailResult {

        private final Long id;
        private final String title;
        private final String description;
        private final Long budget;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;

        public static JobDetailResult of(JobDetailData data, List<SpecialtyCategoryResult> specialtyCategories) {
            Job job = data.getJob();
            return JobDetailResult.builder()
                    .id(job.getId())
                    .title(job.getTitle())
                    .description(job.getDescription())
                    .budget(job.getBudget())
                    .specialtyCategories(specialtyCategories)
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .revisionCount(job.getRevisionCount())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobSubmissionDetailData {

        private final Job job;
        private final JobSubmission submission;

        public static JobSubmissionDetailData of(Job job, JobSubmission submission) {
            return new JobSubmissionDetailData(job, submission);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobSubmissionDetailResult {

        private final Long submissionId;
        private final String title;
        private final String studentName;
        private final String submissionType;
        private final List<String> fileUrls;
        private final String message;
        private final Integer revisionNumber;

        public static JobSubmissionDetailResult of(JobSubmissionDetailData data, User student) {
            JobSubmission submission = data.getSubmission();
            return JobSubmissionDetailResult.builder()
                    .submissionId(submission.getId())
                    .title(data.getJob().getTitle())
                    .studentName(student.getName())
                    .submissionType(submission.getSubmissionType().name())
                    .fileUrls(List.copyOf(submission.getFileUrls()))
                    .message(submission.getMessage())
                    .revisionNumber(submission.getRevisionNumber())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobResultData {

        private final Job job;
        private final List<JobSubmission> submissions;
        private final JobSubmission approvedSubmission;

        /** submissions는 수정 번호 오름차순이고 마지막이 승인된 제출물이다. */
        public static JobResultData of(Job job, List<JobSubmission> submissions, JobSubmission approvedSubmission) {
            return new JobResultData(job, List.copyOf(submissions), approvedSubmission);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobResultResult {

        private final Long jobId;
        private final String title;
        private final String studentName;
        private final LocalDate completedAt;
        private final boolean normalCompleted;
        private final Long workFee;
        private final List<String> fileUrls;
        private final String message;
        private final List<WorkHistoryResult> workHistory;

        /**
         * 결과물과 작업 이력을 만든다. 날짜는 서버 로컬 시각 기준이다.
         * 이력은 시작 → 제출물별(제출, 수정 요청) → 완료 순이며, 요청 시각이 기록되지 않은 과거 수정 요청은 날짜가 null이다.
         * @param startedAt 결제 승인일
         */
        public static JobResultResult of(JobResultData data, User student, LocalDate startedAt) {
            Job job = data.getJob();
            JobSubmission approved = data.getApprovedSubmission();
            LocalDate completedAt = job.getCompletedAt().toLocalDate();

            List<WorkHistoryResult> workHistory = new ArrayList<>();
            workHistory.add(WorkHistoryResult.of(JobWorkHistoryType.STARTED, startedAt));
            for (JobSubmission submission : data.getSubmissions()) {
                JobWorkHistoryType submittedType = submission.getSubmissionType() == JobSubmissionType.DRAFT
                        ? JobWorkHistoryType.DRAFT_SUBMITTED
                        : JobWorkHistoryType.REVISION_SUBMITTED;
                workHistory.add(WorkHistoryResult.of(submittedType, toLocalDate(submission.getCreatedAt())));
                if (submission.getReviewStatus() == JobSubmissionReviewStatus.REVISION_REQUESTED) {
                    workHistory.add(WorkHistoryResult.of(
                            JobWorkHistoryType.REVISION_REQUESTED, toLocalDate(submission.getReviewedAt())));
                }
            }
            workHistory.add(WorkHistoryResult.of(JobWorkHistoryType.COMPLETED, completedAt));

            return JobResultResult.builder()
                    .jobId(job.getId())
                    .title(job.getTitle())
                    .studentName(student.getName())
                    .completedAt(completedAt)
                    .normalCompleted(true)
                    .workFee(job.getBudget())
                    .fileUrls(List.copyOf(approved.getFileUrls()))
                    .message(approved.getMessage())
                    .workHistory(List.copyOf(workHistory))
                    .build();
        }

        private static LocalDate toLocalDate(LocalDateTime dateTime) {
            return dateTime == null ? null : dateTime.toLocalDate();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class WorkHistoryResult {

        private final JobWorkHistoryType type;
        private final LocalDate date;

        public static WorkHistoryResult of(JobWorkHistoryType type, LocalDate date) {
            return new WorkHistoryResult(type, date);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PrepareSubmissionFileUploadResult {

        private final String uploadUrl;
        private final Map<String, String> uploadHeaders;
        private final LocalDateTime uploadUrlExpiresAt;
        private final String fileUrl;

        public static PrepareSubmissionFileUploadResult of(String uploadUrl, Map<String, String> uploadHeaders,
                LocalDateTime uploadUrlExpiresAt, String fileUrl) {
            return new PrepareSubmissionFileUploadResult(uploadUrl, uploadHeaders, uploadUrlExpiresAt, fileUrl);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobSubmissionCreateResult {

        private final Long submissionId;
        private final Long jobId;
        private final String submissionType;
        private final Integer revisionNumber;
        private final String reviewStatus;

        public static JobSubmissionCreateResult from(JobSubmission submission) {
            return JobSubmissionCreateResult.builder()
                    .submissionId(submission.getId())
                    .jobId(submission.getJobId())
                    .submissionType(submission.getSubmissionType().name())
                    .revisionNumber(submission.getRevisionNumber())
                    .reviewStatus(submission.getReviewStatus().name())
                    .build();
        }
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
    public static class StudentMatchedJobData {

        private final Job job;
        private final List<Long> specialtyIds;
        private final JobSubmission latestSubmission;

        public static StudentMatchedJobData of(Job job, List<Long> specialtyIds, JobSubmission latestSubmission) {
            return new StudentMatchedJobData(job, specialtyIds, latestSubmission);
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
    public static class StudentMatchedJobListResult {

        private final List<StudentMatchedJobResult> jobs;

        public static StudentMatchedJobListResult of(List<StudentMatchedJobResult> jobs) {
            return new StudentMatchedJobListResult(jobs);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentMatchedJobResult {

        private final Long jobId;
        private final String title;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final String submissionType;
        private final String reviewStatus;

        public static StudentMatchedJobResult of(
                StudentMatchedJobData data, List<SpecialtyCategoryResult> specialtyCategories) {
            Job job = data.getJob();
            JobSubmission latest = data.getLatestSubmission();
            return StudentMatchedJobResult.builder()
                    .jobId(job.getId())
                    .title(job.getTitle())
                    .specialtyCategories(specialtyCategories)
                    .budget(job.getBudget())
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .revisionCount(job.getRevisionCount())
                    .submissionType(latest == null ? null : latest.getSubmissionType().name())
                    .reviewStatus(latest == null ? null : latest.getReviewStatus().name())
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
