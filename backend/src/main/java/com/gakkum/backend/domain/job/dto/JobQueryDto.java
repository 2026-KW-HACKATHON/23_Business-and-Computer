package com.gakkum.backend.domain.job.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.certificate.entity.StudentCertificate;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.RefundedPaymentData;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;

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
        private final JobProgressStage progressStage;

        public static JobDetailData of(Job job, List<Long> specialtyIds, JobProgressStage progressStage) {
            return new JobDetailData(job, specialtyIds, progressStage);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobDetailResult {

        private final Long id;
        private final String title;
        private final String description;
        private final List<String> referenceImageUrls;
        private final Long budget;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final JobProgressStage progressStage;
        private final String status;
        // 조회한 학생 본인의 지원서 상태. 지원 이력이 없거나 학생 프로필이 없는 학생, 학생이 아닌 사용자는 null이다
        private final JobApplicationStatus applied;
        private final String storeName;
        private final String storeAddress;
        // 아래 취소 정보는 취소된 의뢰를 의뢰한 사장님 또는 선정 학생이 조회할 때만 채우고, 그 외에는 모두 null
        private final String cancelledBy;
        private final String cancelReason;
        private final String messageToStudent;
        private final Long refundAmount;
        private final Long studentCompensationAmount;
        private final LocalDateTime cancelledAt;

        public static JobDetailResult of(JobDetailData data, List<SpecialtyCategoryResult> specialtyCategories,
                Owner owner, JobApplicationStatus applied) {
            return base(data, specialtyCategories, owner, applied).build();
        }

        /**
         * 취소된 의뢰의 당사자에게 내리는 상세. 현재 취소는 사장님만 할 수 있어 취소한 사람은 항상 OWNER다.
         * @param refund 결제 전(모집 중) 취소면 null이고 금액은 0으로 내린다
         */
        public static JobDetailResult ofCancelled(JobDetailData data, List<SpecialtyCategoryResult> specialtyCategories,
                Owner owner, JobApplicationStatus applied, RefundedPaymentData refund) {
            Job job = data.getJob();
            return base(data, specialtyCategories, owner, applied)
                    .cancelledBy(UserRole.OWNER.name())
                    .cancelReason(job.getCancelReason())
                    .messageToStudent(job.getMessageToStudent())
                    .refundAmount(refund == null ? 0L : refund.refundAmount())
                    .studentCompensationAmount(refund == null ? 0L : refund.studentCompensationAmount())
                    .cancelledAt(job.getCompletedAt())
                    .build();
        }

        private static JobDetailResultBuilder base(JobDetailData data,
                List<SpecialtyCategoryResult> specialtyCategories, Owner owner, JobApplicationStatus applied) {
            Job job = data.getJob();
            return JobDetailResult.builder()
                    .id(job.getId())
                    .title(job.getTitle())
                    .description(job.getDescription())
                    .referenceImageUrls(List.copyOf(job.getReferenceImageUrls()))
                    .budget(job.getBudget())
                    .specialtyCategories(specialtyCategories)
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .revisionCount(job.getRevisionCount())
                    .progressStage(data.getProgressStage())
                    .status(job.getStatus().name())
                    .applied(applied)
                    .storeName(owner.getStoreName())
                    .storeAddress(owner.getStoreAddress());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReviewedJobData {

        private final Job job;
        private final JobSubmission approvedSubmission;

        public static ReviewedJobData of(Job job, JobSubmission approvedSubmission) {
            return new ReviewedJobData(job, approvedSubmission);
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
         * @param startedAt 작업 시작일. 일반 의뢰는 결제 승인일, 제안 의뢰는 학생이 작업을 시작한 날
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
    public static class JobApplicationCreateResult {

        private final Long jobApplicationId;

        public static JobApplicationCreateResult from(JobApplication application) {
            return JobApplicationCreateResult.builder()
                    .jobApplicationId(application.getId())
                    .build();
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
        private final JobProgressStage progressStage;

        public static OpenJobData of(
                Job job, List<Long> specialtyIds, Integer applicantCount, JobProgressStage progressStage) {
            return new OpenJobData(job, specialtyIds, applicantCount, progressStage);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MatchedJobData {

        private final Job job;
        private final List<Long> specialtyIds;
        private final JobSubmission pendingSubmission;
        private final JobProgressStage progressStage;

        public static MatchedJobData of(
                Job job, List<Long> specialtyIds, JobSubmission pendingSubmission, JobProgressStage progressStage) {
            return new MatchedJobData(job, specialtyIds, pendingSubmission, progressStage);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentMatchedJobData {

        private final Job job;
        private final List<Long> specialtyIds;
        private final JobSubmission latestSubmission;
        private final JobProgressStage progressStage;

        public static StudentMatchedJobData of(
                Job job, List<Long> specialtyIds, JobSubmission latestSubmission, JobProgressStage progressStage) {
            return new StudentMatchedJobData(job, specialtyIds, latestSubmission, progressStage);
        }
    }

    /** 학생이 지원한 모집 중 의뢰와 본인의 대기 중 지원서. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentAppliedJobData {

        private final Job job;
        private final JobApplication application;
        private final List<Long> specialtyIds;

        public static StudentAppliedJobData of(Job job, JobApplication application, List<Long> specialtyIds) {
            return new StudentAppliedJobData(job, application, specialtyIds);
        }
    }

    /** 탐색 목록의 의뢰 카드 재료. 진행 단계는 의뢰 상태와 최신 제출물에서 계산한 값이다. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ExploreJobData {

        private final Job job;
        private final List<Long> specialtyIds;
        private final JobProgressStage progressStage;

        public static ExploreJobData of(Job job, List<Long> specialtyIds, JobProgressStage progressStage) {
            return new ExploreJobData(job, List.copyOf(specialtyIds), progressStage);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ClosedJobData {

        private final Job job;
        private final List<Long> specialtyIds;
        private final JobProgressStage progressStage;

        public static ClosedJobData of(Job job, List<Long> specialtyIds, JobProgressStage progressStage) {
            return new ClosedJobData(job, specialtyIds, progressStage);
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
        private final JobProgressStage progressStage;

        public static ClosedJobResult of(
                ClosedJobData data, Student student, User worker, List<SpecialtyCategoryResult> specialtyCategories) {
            Job job = data.getJob();
            return ClosedJobResult.builder()
                    .jobId(job.getId())
                    .title(job.getTitle())
                    .specialtyCategories(specialtyCategories)
                    .matchedWorker(student == null ? null : MatchedWorkerResult.of(student.getId(), worker.getName()))
                    .completedAt(job.getCompletedAt().toLocalDate())
                    .progressStage(data.getProgressStage())
                    .build();
        }
    }

    /** 취소된 의뢰와 취소 전 결제 완료(MATCHED) 여부 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CancelledJobData {

        private final Job job;
        private final boolean paid;

        public static CancelledJobData of(Job job, boolean paid) {
            return new CancelledJobData(job, paid);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobCancelResult {

        private final Long jobId;
        private final String status;
        private final Long paidAmount;
        private final Long studentCompensationAmount;
        private final Long refundAmount;
        private final LocalDateTime cancelledAt;
        private final String cancelReason;
        private final String messageToStudent;

        public static JobCancelResult of(Job job, RefundedPaymentData refund) {
            return JobCancelResult.builder()
                    .jobId(job.getId())
                    .status(job.getStatus().name())
                    .paidAmount(refund == null ? 0L : refund.amount())
                    .studentCompensationAmount(refund == null ? 0L : refund.studentCompensationAmount())
                    .refundAmount(refund == null ? 0L : refund.refundAmount())
                    .cancelledAt(job.getCompletedAt())
                    .cancelReason(job.getCancelReason())
                    .messageToStudent(job.getMessageToStudent())
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
        private final JobProgressStage progressStage;

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
                    .progressStage(data.getProgressStage())
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
        private final JobProgressStage progressStage;

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
                    .progressStage(data.getProgressStage())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentAppliedJobListResult {

        private final List<StudentAppliedJobResult> jobs;

        public static StudentAppliedJobListResult of(List<StudentAppliedJobResult> jobs) {
            return new StudentAppliedJobListResult(jobs);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentAppliedJobResult {

        private final Long jobId;
        private final Long jobApplicationId;
        private final String title;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final JobStatus jobStatus;
        private final JobApplicationStatus applicationStatus;
        private final LocalDateTime appliedAt;

        public static StudentAppliedJobResult of(
                StudentAppliedJobData data, List<SpecialtyCategoryResult> specialtyCategories) {
            Job job = data.getJob();
            JobApplication application = data.getApplication();
            return StudentAppliedJobResult.builder()
                    .jobId(job.getId())
                    .jobApplicationId(application.getId())
                    .title(job.getTitle())
                    .specialtyCategories(specialtyCategories)
                    .budget(job.getBudget())
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .jobStatus(job.getStatus())
                    .applicationStatus(application.getStatus())
                    .appliedAt(application.getCreatedAt())
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
        private final JobProgressStage progressStage;

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
                    .progressStage(data.getProgressStage())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobApplicationListData {

        private final Job job;
        private final List<Long> specialtyIds;
        private final List<JobApplication> applications;

        /** applications는 대기 중(PENDING) 지원서 전체이고 정렬되지 않은 상태다. */
        public static JobApplicationListData of(
                Job job, List<Long> specialtyIds, List<JobApplication> applications) {
            return new JobApplicationListData(job, specialtyIds, List.copyOf(applications));
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobApplicationListResult {

        private final JobApplicationJobResult job;
        private final Integer applicantCount;
        private final List<JobApplicantResult> applicants;

        /** 지원자 수는 반환하는 지원자 목록의 길이다. */
        public static JobApplicationListResult of(JobApplicationJobResult job, List<JobApplicantResult> applicants) {
            return new JobApplicationListResult(job, applicants.size(), applicants);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobApplicationJobResult {

        private final Long jobId;
        private final String title;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;

        public static JobApplicationJobResult of(Job job, List<SpecialtyCategoryResult> specialtyCategories) {
            return JobApplicationJobResult.builder()
                    .jobId(job.getId())
                    .title(job.getTitle())
                    .specialtyCategories(specialtyCategories)
                    .budget(job.getBudget())
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobApplicantResult {

        private final Long jobApplicationId;
        private final Long studentProfileId;
        private final String profileImageUrl;
        private final String name;
        private final String studentNumber;
        private final String major;
        private final BigDecimal averageRating;
        private final Long completedJobCount;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final String summary;
        private final String workPlan;
        private final String deliveryMethod;
        private final LocalDateTime appliedAt;

        /** appliedAt은 정렬에만 쓰고 응답에는 내리지 않는다. 지원 시각이 없는 기존 데이터는 null이다. */
        public static JobApplicantResult of(
                JobApplication application,
                Student student,
                User studentUser,
                BigDecimal averageRating,
                Long completedJobCount,
                List<SpecialtyCategoryResult> specialtyCategories) {
            return JobApplicantResult.builder()
                    .jobApplicationId(application.getId())
                    .studentProfileId(student.getId())
                    .profileImageUrl(student.getProfileImageUrl())
                    .name(studentUser.getName())
                    .studentNumber(student.getStudentNumber())
                    .major(student.getMajor())
                    .averageRating(averageRating)
                    .completedJobCount(completedJobCount)
                    .specialtyCategories(specialtyCategories)
                    .summary(application.getSummary())
                    .workPlan(application.getWorkPlan())
                    .deliveryMethod(application.getDeliveryMethod())
                    .appliedAt(application.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobApplicantProfileResult {

        private final ApplicantStudentResult student;
        private final Long proposalCount;
        private final Long completedJobCount;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final List<ApplicantCertificateResult> certificates;
        private final String portfolioUrl;
        private final Integer penaltyCount;
        private final Integer reviewCount;
        private final List<ApplicantReviewResult> reviews;

        /** 리뷰 수는 반환하는 리뷰 목록의 길이다. */
        public static JobApplicantProfileResult of(
                Student student,
                User studentUser,
                long proposalCount,
                long completedJobCount,
                List<SpecialtyCategoryResult> specialtyCategories,
                List<StudentCertificate> certificates,
                List<ApplicantReviewResult> reviews) {
            return JobApplicantProfileResult.builder()
                    .student(ApplicantStudentResult.of(student, studentUser))
                    .proposalCount(proposalCount)
                    .completedJobCount(completedJobCount)
                    .specialtyCategories(specialtyCategories)
                    .certificates(certificates.stream()
                            .map(ApplicantCertificateResult::from)
                            .toList())
                    .portfolioUrl(student.getPortfolioUrl())
                    .penaltyCount(student.getPenaltyCount())
                    .reviewCount(reviews.size())
                    .reviews(List.copyOf(reviews))
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ApplicantStudentResult {

        private final Long studentProfileId;
        private final String name;
        private final String university;
        private final String major;
        private final String studentNumber;

        public static ApplicantStudentResult of(Student student, User studentUser) {
            return ApplicantStudentResult.builder()
                    .studentProfileId(student.getId())
                    .name(studentUser.getName())
                    .university(student.getUniversity())
                    .major(student.getMajor())
                    .studentNumber(student.getStudentNumber())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ApplicantCertificateResult {

        private final String certificateName;
        private final Integer acquiredYear;

        public static ApplicantCertificateResult from(StudentCertificate certificate) {
            return new ApplicantCertificateResult(certificate.getCertificateName(), certificate.getAcquiredYear());
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ApplicantReviewResult {

        private final String storeName;
        private final String jobTitle;
        private final String content;
        private final Integer rating;
        private final LocalDate createdAt;

        /** 작성일은 서버 로컬 시각 기준 날짜만 내린다. 매장 이름과 의뢰 제목은 현재 값이다. */
        public static ApplicantReviewResult of(Review review, String jobTitle, String storeName) {
            return ApplicantReviewResult.builder()
                    .storeName(storeName)
                    .jobTitle(jobTitle)
                    .content(review.getContent())
                    .rating(review.getRating())
                    .createdAt(review.getCreatedAt() == null ? null : review.getCreatedAt().toLocalDate())
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
