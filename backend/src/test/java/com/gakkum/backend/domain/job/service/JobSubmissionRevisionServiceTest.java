package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobSubmissionRevisionServiceTest {

    private static final String FILE_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/job-submissions/42/7/x/a.pdf";

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, mock(JobSpecialtyRepository.class), mock(JobApplicationRepository.class),
            jobSubmissionRepository, Clock.systemUTC());

    @Test
    @DisplayName("초안에 수정 요청이 오면 첫 수정안을 REVISION, 수정 번호 1, 검토 대기로 저장한다")
    void savesFirstRevision() {
        givenLockedJob(JobStatus.MATCHED, 2);
        givenLatest(0, JobSubmissionReviewStatus.REVISION_REQUESTED);
        givenSaveReturnsArgument();

        JobSubmission saved = jobService.submitRevision(command(), 7L);

        assertThat(saved.getJobId()).isEqualTo(42L);
        assertThat(saved.getSubmissionType()).isEqualTo(JobSubmissionType.REVISION);
        assertThat(saved.getRevisionNumber()).isEqualTo(1);
        assertThat(saved.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
        assertThat(saved.getFileUrls()).containsExactly(FILE_URL);
        assertThat(saved.getMessage()).isEqualTo("반영했습니다.");
    }

    @Test
    @DisplayName("후속 수정안은 최신 수정 번호에 1을 더하고, 수정 가능 횟수와 같은 번호까지 허용한다")
    void savesFollowingRevisionUpToLimit() {
        givenLockedJob(JobStatus.MATCHED, 2);
        givenLatest(1, JobSubmissionReviewStatus.REVISION_REQUESTED);
        givenSaveReturnsArgument();

        assertThat(jobService.submitRevision(command(), 7L).getRevisionNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("다음 번호가 수정 가능 횟수를 넘으면 JOB_SUBMISSION_409_REVISION_LIMIT으로 거부한다")
    void rejectsRevisionOverLimit() {
        givenLockedJob(JobStatus.MATCHED, 2);
        givenLatest(2, JobSubmissionReviewStatus.REVISION_REQUESTED);

        assertError(() -> jobService.submitRevision(command(), 7L), ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);
        verify(jobSubmissionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("수정 가능 횟수가 0이면 첫 수정안도 JOB_SUBMISSION_409_REVISION_LIMIT으로 거부한다")
    void rejectsRevisionWhenNoRevisionAllowed() {
        givenLockedJob(JobStatus.MATCHED, 0);
        givenLatest(0, JobSubmissionReviewStatus.REVISION_REQUESTED);

        assertError(() -> jobService.submitRevision(command(), 7L), ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);
    }

    @ParameterizedTest
    @EnumSource(value = JobSubmissionReviewStatus.class, names = { "PENDING", "APPROVED" })
    @DisplayName("최신 제출물이 검토 대기·승인이면 JOB_SUBMISSION_409_REVISION_NOT_REQUESTED로 거부한다")
    void rejectsWhenRevisionNotRequested(JobSubmissionReviewStatus status) {
        givenLockedJob(JobStatus.MATCHED, 2);
        givenLatest(0, status);

        assertError(() -> jobService.submitRevision(command(), 7L),
                ErrorCode.JOB_SUBMISSION_REVISION_NOT_REQUESTED);
        verify(jobSubmissionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("제출물이 하나도 없으면 JOB_SUBMISSION_409_REVISION_NOT_REQUESTED로 거부한다")
    void rejectsWithoutAnySubmission() {
        givenLockedJob(JobStatus.MATCHED, 2);
        when(jobSubmissionRepository.findFirstByJobIdOrderByRevisionNumberDesc(42L)).thenReturn(Optional.empty());

        assertError(() -> jobService.submitRevision(command(), 7L),
                ErrorCode.JOB_SUBMISSION_REVISION_NOT_REQUESTED);
    }

    @Test
    @DisplayName("매칭된 학생이 아니면 JOB_SUBMISSION_403으로 거부하고 제출물을 조회하지 않는다")
    void rejectsOtherStudent() {
        givenLockedJob(JobStatus.MATCHED, 2);

        assertError(() -> jobService.submitRevision(command(), 8L), ErrorCode.JOB_SUBMISSION_FORBIDDEN);
        verify(jobSubmissionRepository, never()).findFirstByJobIdOrderByRevisionNumberDesc(any());
    }

    @Test
    @DisplayName("의뢰가 MATCHED 상태가 아니면 JOB_SUBMISSION_409_STATUS로 거부한다")
    void rejectsJobNotMatched() {
        givenLockedJob(JobStatus.CLOSED, 2);

        assertError(() -> jobService.submitRevision(command(), 7L), ErrorCode.JOB_SUBMISSION_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("동시 제출로 유니크 제약이 충돌하면 JOB_SUBMISSION_409_REVISION_NOT_REQUESTED로 바꿔 응답한다")
    void convertsUniqueViolation() {
        givenLockedJob(JobStatus.MATCHED, 2);
        givenLatest(0, JobSubmissionReviewStatus.REVISION_REQUESTED);
        when(jobSubmissionRepository.saveAndFlush(any(JobSubmission.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertError(() -> jobService.submitRevision(command(), 7L),
                ErrorCode.JOB_SUBMISSION_REVISION_NOT_REQUESTED);
    }

    @Test
    @DisplayName("최종 마감일이 지났어도 수정안 제출을 허용한다")
    void allowsRevisionAfterFinalDeadline() {
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(Job.builder()
                .id(42L)
                .status(JobStatus.MATCHED)
                .selectedStudentProfileId(7L)
                .revisionCount(2)
                .finalDeadline(LocalDate.of(2000, 1, 1))
                .build()));
        givenLatest(0, JobSubmissionReviewStatus.REVISION_REQUESTED);
        givenSaveReturnsArgument();

        assertThat(jobService.submitRevision(command(), 7L).getRevisionNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("수정안 사전 확인도 같은 수정 요청·횟수 조건을 적용한다")
    void precheckAppliesSameRules() {
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job(JobStatus.MATCHED, 1)));
        givenLatest(1, JobSubmissionReviewStatus.REVISION_REQUESTED);

        assertError(() -> jobService.validateRevisionSubmittable(42L, 7L),
                ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);
    }

    private void givenLockedJob(JobStatus status, int revisionCount) {
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job(status, revisionCount)));
    }

    private void givenLatest(int revisionNumber, JobSubmissionReviewStatus reviewStatus) {
        when(jobSubmissionRepository.findFirstByJobIdOrderByRevisionNumberDesc(42L))
                .thenReturn(Optional.of(JobSubmission.builder()
                        .jobId(42L)
                        .submissionType(revisionNumber == 0 ? JobSubmissionType.DRAFT : JobSubmissionType.REVISION)
                        .revisionNumber(revisionNumber)
                        .reviewStatus(reviewStatus)
                        .build()));
    }

    private void givenSaveReturnsArgument() {
        when(jobSubmissionRepository.saveAndFlush(any(JobSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateJobSubmissionCommand command() {
        return CreateJobSubmissionCommand.of("KAKAO_1", 42L, List.of(FILE_URL), "반영했습니다.");
    }

    private Job job(JobStatus status, int revisionCount) {
        return Job.builder()
                .id(42L)
                .status(status)
                .selectedStudentProfileId(7L)
                .revisionCount(revisionCount)
                .build();
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
