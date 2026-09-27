package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CompleteJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetClosedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobSubmissionCompleteServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T03:15:30Z");
    private static final LocalDateTime EXPECTED_COMPLETED_AT = LocalDateTime.ofInstant(NOW, ZoneId.systemDefault());

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, mock(JobSpecialtyRepository.class), mock(JobApplicationRepository.class),
            jobSubmissionRepository, mock(SpecialtyService.class), Clock.fixed(NOW, ZoneId.of("UTC")));

    @ParameterizedTest
    @EnumSource(JobSubmissionType.class)
    @DisplayName("검토 대기 초안·수정안을 완료하면 제출물은 APPROVED, 의뢰는 CLOSED가 되고 완료 시각을 기록한다")
    void completesPendingSubmission(JobSubmissionType type) {
        Job job = givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(81L, 42L, type, JobSubmissionReviewStatus.PENDING);

        jobService.completeSubmission(command(81L));

        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.APPROVED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(job.getCompletedAt()).isEqualTo(EXPECTED_COMPLETED_AT);
        verify(jobRepository).findByIdAndOwnerProfileId(42L, 5L);
    }

    @Test
    @DisplayName("완료된 의뢰는 완료 시각이 있어 사장님 완료 목록에 표시된다")
    void completedJobAppearsInClosedList() {
        Job job = givenOwnedJob(JobStatus.MATCHED);
        givenSubmission(81L, 42L, JobSubmissionType.DRAFT, JobSubmissionReviewStatus.PENDING);
        jobService.completeSubmission(command(81L));
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCompletedAtDescIdDesc(5L, JobStatus.CLOSED))
                .thenReturn(List.of(job));

        List<ClosedJobData> closedJobs = jobService.getClosedJobs(GetClosedJobsCommand.of(5L));

        assertThat(closedJobs).extracting(data -> data.getJob().getId()).containsExactly(42L);
        assertThat(closedJobs.get(0).getJob().getCompletedAt()).isEqualTo(EXPECTED_COMPLETED_AT);
    }

    @ParameterizedTest
    @EnumSource(value = JobSubmissionReviewStatus.class, names = { "REVISION_REQUESTED", "APPROVED" })
    @DisplayName("이미 검토된 제출물(중복 완료·수정 요청된 제출물)은 JOB_SUBMISSION_409_REVIEWED로 거부하고 의뢰를 종료하지 않는다")
    void rejectsAlreadyReviewedSubmission(JobSubmissionReviewStatus status) {
        Job job = givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(81L, 42L, JobSubmissionType.DRAFT, status);

        assertError(() -> jobService.completeSubmission(command(81L)), ErrorCode.JOB_SUBMISSION_ALREADY_REVIEWED);
        assertThat(submission.getReviewStatus()).isEqualTo(status);
        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(job.getCompletedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = { "OPEN", "CLOSED" })
    @DisplayName("진행 중(MATCHED)이 아닌 의뢰이면 JOB_SUBMISSION_409_REVIEW_STATUS로 거부한다")
    void rejectsJobNotMatched(JobStatus status) {
        Job job = givenOwnedJob(status);
        JobSubmission submission = givenSubmission(81L, 42L, JobSubmissionType.DRAFT, JobSubmissionReviewStatus.PENDING);

        assertError(() -> jobService.completeSubmission(command(81L)), ErrorCode.JOB_SUBMISSION_REVIEW_NOT_AVAILABLE);
        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
        assertThat(job.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("다른 사업주의 의뢰이면 JOB_404로 거부하고 제출물을 조회하지 않는다")
    void rejectsOtherOwnersJob() {
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.empty());

        assertError(() -> jobService.completeSubmission(command(81L)), ErrorCode.JOB_NOT_FOUND);
        verify(jobSubmissionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("존재하지 않는 제출물이면 JOB_SUBMISSION_404로 거부한다")
    void rejectsMissingSubmission() {
        givenOwnedJob(JobStatus.MATCHED);
        when(jobSubmissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertError(() -> jobService.completeSubmission(command(99L)), ErrorCode.JOB_SUBMISSION_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 의뢰의 제출물이면 JOB_SUBMISSION_404로 거부하고 의뢰를 종료하지 않는다")
    void rejectsSubmissionOfOtherJob() {
        Job job = givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(91L, 43L, JobSubmissionType.DRAFT, JobSubmissionReviewStatus.PENDING);

        assertError(() -> jobService.completeSubmission(command(91L)), ErrorCode.JOB_SUBMISSION_NOT_FOUND);
        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
    }

    private Job givenOwnedJob(JobStatus status) {
        Job job = Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .status(status)
                .selectedStudentProfileId(status == JobStatus.OPEN ? null : 7L)
                .revisionCount(2)
                .build();
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(job));
        return job;
    }

    private JobSubmission givenSubmission(
            Long id, Long jobId, JobSubmissionType type, JobSubmissionReviewStatus reviewStatus) {
        JobSubmission submission = JobSubmission.builder()
                .id(id)
                .jobId(jobId)
                .submissionType(type)
                .revisionNumber(type == JobSubmissionType.DRAFT ? 0 : 1)
                .reviewStatus(reviewStatus)
                .build();
        when(jobSubmissionRepository.findById(id)).thenReturn(Optional.of(submission));
        return submission;
    }

    private CompleteJobSubmissionCommand command(Long submissionId) {
        return CompleteJobSubmissionCommand.of(42L, submissionId, 5L);
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
