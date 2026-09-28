package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobReviewableServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, mock(JobSpecialtyRepository.class), mock(JobApplicationRepository.class),
            mock(JobSubmissionRepository.class), Clock.systemUTC());

    @Test
    @DisplayName("사장님 본인의 완료된 의뢰를 잠금 조회로 반환한다")
    void returnsOwnedClosedJob() {
        Job job = givenOwnedJob(JobStatus.CLOSED, 7L);

        assertThat(jobService.getReviewableJobForUpdate(42L, 5L)).isSameAs(job);
    }

    @Test
    @DisplayName("다른 사장님의 의뢰이거나 없는 의뢰면 JOB_404로 거부한다")
    void rejectsOtherOwnersOrMissingJob() {
        when(jobRepository.findByIdAndOwnerProfileId(42L, 6L)).thenReturn(Optional.empty());

        assertError(() -> jobService.getReviewableJobForUpdate(42L, 6L), ErrorCode.JOB_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = "CLOSED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("완료되지 않은 의뢰는 REVIEW_409_STATUS로 거부한다")
    void rejectsNotClosedJob(JobStatus status) {
        givenOwnedJob(status, 7L);

        assertError(() -> jobService.getReviewableJobForUpdate(42L, 5L), ErrorCode.REVIEW_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("완료된 의뢰에 담당 학생이 없으면 데이터 이상으로 보고 COMMON_500으로 거부한다")
    void rejectsClosedJobWithoutStudent() {
        givenOwnedJob(JobStatus.CLOSED, null);

        assertError(() -> jobService.getReviewableJobForUpdate(42L, 5L), ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private Job givenOwnedJob(JobStatus status, Long selectedStudentProfileId) {
        Job job = Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .status(status)
                .selectedStudentProfileId(selectedStudentProfileId)
                .completedAt(LocalDateTime.of(2026, 9, 20, 15, 0))
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
