package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobResultCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobResultData;
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

class JobResultServiceTest {

    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 9, 20, 15, 0);

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, mock(JobSpecialtyRepository.class), mock(JobApplicationRepository.class),
            jobSubmissionRepository, Clock.systemUTC());

    @Test
    @DisplayName("의뢰한 사장님은 완료된 의뢰의 제출물을 수정 번호 순으로, 마지막 승인 제출물과 함께 조회한다")
    void ownerGetsResult() {
        Job job = givenJob(JobStatus.CLOSED, COMPLETED_AT);
        JobSubmission draft = submission(81L, 0, JobSubmissionReviewStatus.REVISION_REQUESTED);
        JobSubmission revision = submission(82L, 1, JobSubmissionReviewStatus.APPROVED);
        when(jobSubmissionRepository.findByJobIdOrderByRevisionNumberAsc(42L)).thenReturn(List.of(draft, revision));

        JobResultData data = jobService.getJobResult(GetJobResultCommand.ofOwner(42L, 5L));

        assertThat(data.getJob()).isSameAs(job);
        assertThat(data.getSubmissions()).containsExactly(draft, revision);
        assertThat(data.getApprovedSubmission()).isSameAs(revision);
    }

    @Test
    @DisplayName("담당 학생은 완료된 의뢰의 결과물을 조회한다")
    void assignedStudentGetsResult() {
        givenJob(JobStatus.CLOSED, COMPLETED_AT);
        JobSubmission draft = submission(81L, 0, JobSubmissionReviewStatus.APPROVED);
        when(jobSubmissionRepository.findByJobIdOrderByRevisionNumberAsc(42L)).thenReturn(List.of(draft));

        JobResultData data = jobService.getJobResult(GetJobResultCommand.ofStudent(42L, 7L));

        assertThat(data.getApprovedSubmission()).isSameAs(draft);
    }

    @Test
    @DisplayName("다른 사장님의 의뢰이면 JOB_RESULT_404로 거부하고 제출물을 조회하지 않는다")
    void rejectsOtherOwner() {
        givenJob(JobStatus.CLOSED, COMPLETED_AT);

        assertError(() -> jobService.getJobResult(GetJobResultCommand.ofOwner(42L, 6L)), ErrorCode.JOB_RESULT_NOT_FOUND);
        verify(jobSubmissionRepository, never()).findByJobIdOrderByRevisionNumberAsc(any());
    }

    @Test
    @DisplayName("담당하지 않은 학생이면 JOB_RESULT_404로 거부한다")
    void rejectsOtherStudent() {
        givenJob(JobStatus.CLOSED, COMPLETED_AT);

        assertError(() -> jobService.getJobResult(GetJobResultCommand.ofStudent(42L, 8L)), ErrorCode.JOB_RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("사장님 프로필 ID가 학생 프로필 ID와 같아도 사장님 소유 관계로만 판단한다")
    void ownerIsNotMatchedByStudentProfileId() {
        givenJob(JobStatus.CLOSED, COMPLETED_AT);

        assertError(() -> jobService.getJobResult(GetJobResultCommand.ofOwner(42L, 7L)), ErrorCode.JOB_RESULT_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = { "OPEN", "MATCHED" })
    @DisplayName("아직 완료되지 않은 의뢰이면 사장님에게도 JOB_RESULT_404로 거부한다")
    void rejectsJobNotClosed(JobStatus status) {
        givenJob(status, null);

        assertError(() -> jobService.getJobResult(GetJobResultCommand.ofOwner(42L, 5L)), ErrorCode.JOB_RESULT_NOT_FOUND);
        verify(jobSubmissionRepository, never()).findByJobIdOrderByRevisionNumberAsc(any());
    }

    @Test
    @DisplayName("존재하지 않는 의뢰이면 JOB_RESULT_404로 거부한다")
    void rejectsMissingJob() {
        when(jobRepository.findById(42L)).thenReturn(Optional.empty());

        assertError(() -> jobService.getJobResult(GetJobResultCommand.ofOwner(42L, 5L)), ErrorCode.JOB_RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("완료된 의뢰의 마지막 제출물이 승인 상태가 아니면 데이터 오류로 500을 반환한다")
    void rejectsWhenLastSubmissionNotApproved() {
        givenJob(JobStatus.CLOSED, COMPLETED_AT);
        when(jobSubmissionRepository.findByJobIdOrderByRevisionNumberAsc(42L)).thenReturn(List.of(
                submission(81L, 0, JobSubmissionReviewStatus.APPROVED),
                submission(82L, 1, JobSubmissionReviewStatus.PENDING)));

        assertError(() -> jobService.getJobResult(GetJobResultCommand.ofOwner(42L, 5L)), ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("완료된 의뢰에 제출물이 없거나 완료 시각이 없으면 데이터 오류로 500을 반환한다")
    void rejectsBrokenClosedJob() {
        givenJob(JobStatus.CLOSED, COMPLETED_AT);
        when(jobSubmissionRepository.findByJobIdOrderByRevisionNumberAsc(42L)).thenReturn(List.of());
        assertError(() -> jobService.getJobResult(GetJobResultCommand.ofOwner(42L, 5L)), ErrorCode.INTERNAL_SERVER_ERROR);

        givenJob(JobStatus.CLOSED, null);
        assertError(() -> jobService.getJobResult(GetJobResultCommand.ofOwner(42L, 5L)), ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private Job givenJob(JobStatus status, LocalDateTime completedAt) {
        Job job = Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .status(status)
                .selectedStudentProfileId(7L)
                .completedAt(completedAt)
                .build();
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job));
        return job;
    }

    private JobSubmission submission(Long id, int revisionNumber, JobSubmissionReviewStatus reviewStatus) {
        return JobSubmission.builder()
                .id(id)
                .jobId(42L)
                .submissionType(revisionNumber == 0 ? JobSubmissionType.DRAFT : JobSubmissionType.REVISION)
                .revisionNumber(revisionNumber)
                .reviewStatus(reviewStatus)
                .build();
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
