package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CancelJobCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.CancelledJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobCancelServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T03:15:30Z");
    private static final LocalDateTime EXPECTED_CANCELLED_AT = LocalDateTime.ofInstant(NOW, ZoneId.systemDefault());
    private static final CancelJobCommand COMMAND =
            CancelJobCommand.of("KAKAO_12345", 42L, "매장 일정이 변경되었습니다.", "진행해 주셔서 감사합니다.");

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, mock(JobSpecialtyRepository.class), mock(JobApplicationRepository.class),
            jobSubmissionRepository, Clock.fixed(NOW, ZoneId.of("UTC")));

    @Test
    @DisplayName("모집 중(OPEN) 의뢰를 취소하면 CANCELLED가 되고 결제 전이므로 환불 대상이 아니다")
    void cancelsOpenJobWithoutPayment() {
        Job job = givenOwnedJob(JobStatus.OPEN);

        CancelledJobData result = jobService.cancelJob(COMMAND, 5L);

        assertThat(result.getJob()).isSameAs(job);
        assertThat(result.isPaid()).isFalse();
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.getCompletedAt()).isEqualTo(EXPECTED_CANCELLED_AT);
        assertThat(job.getCancelReason()).isEqualTo("매장 일정이 변경되었습니다.");
        assertThat(job.getMessageToStudent()).isEqualTo("진행해 주셔서 감사합니다.");
    }

    @Test
    @DisplayName("진행 중(MATCHED) 의뢰를 취소하면 CANCELLED가 되고 결제 완료 건이므로 환불 대상이다")
    void cancelsMatchedJobAsPaid() {
        Job job = givenOwnedJob(JobStatus.MATCHED);

        CancelledJobData result = jobService.cancelJob(COMMAND, 5L);

        assertThat(result.isPaid()).isTrue();
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.getCompletedAt()).isEqualTo(EXPECTED_CANCELLED_AT);
        assertThat(job.getCancelReason()).isEqualTo("매장 일정이 변경되었습니다.");
        assertThat(job.getMessageToStudent()).isEqualTo("진행해 주셔서 감사합니다.");
    }

    @Test
    @DisplayName("제출 이력이 있는 진행 중(MATCHED) 의뢰는 JOB_409_CANCEL_SUBMITTED로 거부하고 상태·종료 시각·취소 입력을 바꾸지 않는다")
    void rejectsMatchedJobWithSubmission() {
        Job job = givenOwnedJob(JobStatus.MATCHED);
        when(jobSubmissionRepository.existsByJobId(42L)).thenReturn(true);

        assertError(() -> jobService.cancelJob(COMMAND, 5L), ErrorCode.JOB_CANCEL_SUBMITTED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(job.getCompletedAt()).isNull();
        assertThat(job.getCancelReason()).isNull();
        assertThat(job.getMessageToStudent()).isNull();
    }

    @Test
    @DisplayName("모집 중(OPEN) 의뢰는 제출 이력을 조회하지 않고 취소한다")
    void cancelsOpenJobWithoutCheckingSubmissions() {
        Job job = givenOwnedJob(JobStatus.OPEN);

        jobService.cancelJob(COMMAND, 5L);

        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        verifyNoInteractions(jobSubmissionRepository);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = { "CLOSED", "CANCELLED" })
    @DisplayName("이미 종료되었거나 취소된 의뢰는 JOB_409_CANCEL로 거부하고 상태와 취소 입력을 바꾸지 않는다")
    void rejectsFinishedJob(JobStatus status) {
        Job job = givenOwnedJob(status);

        assertError(() -> jobService.cancelJob(COMMAND, 5L), ErrorCode.JOB_CANCEL_NOT_AVAILABLE);
        assertThat(job.getStatus()).isEqualTo(status);
        assertThat(job.getCompletedAt()).isNull();
        assertThat(job.getCancelReason()).isNull();
        assertThat(job.getMessageToStudent()).isNull();
    }

    @Test
    @DisplayName("다른 사업주의 의뢰이면 JOB_404로 거부한다")
    void rejectsOtherOwnersJob() {
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.empty());

        assertError(() -> jobService.cancelJob(COMMAND, 5L), ErrorCode.JOB_NOT_FOUND);
    }

    private Job givenOwnedJob(JobStatus status) {
        Job job = Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .status(status)
                .selectedStudentProfileId(status == JobStatus.OPEN ? null : 7L)
                .build();
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(job));
        return job;
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
