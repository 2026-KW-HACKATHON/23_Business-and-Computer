package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.gakkum.backend.domain.job.dto.JobCommandDto.RequestJobSubmissionRevisionCommand;
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

class JobSubmissionRevisionRequestServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, mock(JobSpecialtyRepository.class), mock(JobApplicationRepository.class),
            jobSubmissionRepository, mock(SpecialtyService.class), Clock.systemUTC());

    @Test
    @DisplayName("검토 대기 초안에 수정을 요청하면 잠근 의뢰 안에서 REVISION_REQUESTED로 바꾼다")
    void requestsRevisionOnPendingDraft() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);

        jobService.requestRevision(command(81L));

        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.REVISION_REQUESTED);
        verify(jobRepository).findByIdAndOwnerProfileId(42L, 5L);
    }

    @Test
    @DisplayName("마지막 허용 수정안 직전 번호의 수정안에는 수정을 요청할 수 있다")
    void allowsRequestBelowLimit() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(87L, 42L, 1, JobSubmissionReviewStatus.PENDING);

        jobService.requestRevision(command(87L));

        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.REVISION_REQUESTED);
    }

    @Test
    @DisplayName("마지막 허용 수정안까지 제출됐으면 JOB_SUBMISSION_409_REVISION_LIMIT으로 거부한다")
    void rejectsWhenRevisionLimitReached() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(88L, 42L, 2, JobSubmissionReviewStatus.PENDING);

        assertError(() -> jobService.requestRevision(command(88L)), ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);
        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
    }

    @Test
    @DisplayName("수정 가능 횟수가 0이면 초안에도 수정 요청을 거부한다")
    void rejectsWhenNoRevisionAllowed() {
        givenOwnedJob(JobStatus.MATCHED, 0);
        givenSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);

        assertError(() -> jobService.requestRevision(command(81L)), ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);
    }

    @ParameterizedTest
    @EnumSource(value = JobSubmissionReviewStatus.class, names = { "REVISION_REQUESTED", "APPROVED" })
    @DisplayName("이미 처리된 제출물(중복 요청·오래된 제출물 ID)은 JOB_SUBMISSION_409_REVIEWED로 거부한다")
    void rejectsAlreadyReviewedSubmission(JobSubmissionReviewStatus status) {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(81L, 42L, 0, status);

        assertError(() -> jobService.requestRevision(command(81L)), ErrorCode.JOB_SUBMISSION_ALREADY_REVIEWED);
        assertThat(submission.getReviewStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("다른 사업주의 의뢰이면 JOB_404로 거부하고 제출물을 조회하지 않는다")
    void rejectsOtherOwnersJob() {
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.empty());

        assertError(() -> jobService.requestRevision(command(81L)), ErrorCode.JOB_NOT_FOUND);
        verify(jobSubmissionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("존재하지 않는 제출물이면 JOB_SUBMISSION_404로 거부한다")
    void rejectsMissingSubmission() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        when(jobSubmissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertError(() -> jobService.requestRevision(command(99L)), ErrorCode.JOB_SUBMISSION_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 의뢰의 제출물이면 JOB_SUBMISSION_404로 거부한다")
    void rejectsSubmissionOfOtherJob() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(91L, 43L, 0, JobSubmissionReviewStatus.PENDING);

        assertError(() -> jobService.requestRevision(command(91L)), ErrorCode.JOB_SUBMISSION_NOT_FOUND);
        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
    }

    @Test
    @DisplayName("진행 중(MATCHED)이 아닌 의뢰이면 JOB_SUBMISSION_409_REVIEW_STATUS로 거부한다")
    void rejectsJobNotMatched() {
        givenOwnedJob(JobStatus.CLOSED, 2);
        JobSubmission submission = givenSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);

        assertError(() -> jobService.requestRevision(command(81L)), ErrorCode.JOB_SUBMISSION_REVIEW_NOT_AVAILABLE);
        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
    }

    private void givenOwnedJob(JobStatus status, int revisionCount) {
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .status(status)
                .selectedStudentProfileId(7L)
                .revisionCount(revisionCount)
                .build()));
    }

    private JobSubmission givenSubmission(
            Long id, Long jobId, int revisionNumber, JobSubmissionReviewStatus reviewStatus) {
        JobSubmission submission = JobSubmission.builder()
                .id(id)
                .jobId(jobId)
                .submissionType(revisionNumber == 0 ? JobSubmissionType.DRAFT : JobSubmissionType.REVISION)
                .revisionNumber(revisionNumber)
                .reviewStatus(reviewStatus)
                .build();
        when(jobSubmissionRepository.findById(id)).thenReturn(Optional.of(submission));
        return submission;
    }

    private RequestJobSubmissionRevisionCommand command(Long submissionId) {
        return RequestJobSubmissionRevisionCommand.of(42L, submissionId, 5L);
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
