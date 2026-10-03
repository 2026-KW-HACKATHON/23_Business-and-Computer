package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobBatchLookupServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, mock(JobSpecialtyRepository.class), jobApplicationRepository,
            mock(JobSubmissionRepository.class), Clock.systemUTC());

    @Test
    @DisplayName("의뢰 ID 목록을 한 번에 조회해 ID별 의뢰를 반환한다")
    void returnsJobsById() {
        Job first = Job.builder().id(41L).build();
        Job second = Job.builder().id(42L).build();
        when(jobRepository.findAllById(List.of(42L, 41L))).thenReturn(List.of(first, second));

        Map<Long, Job> jobs = jobService.getJobsByIds(List.of(42L, 41L));

        assertThat(jobs).containsOnly(Map.entry(41L, first), Map.entry(42L, second));
    }

    @Test
    @DisplayName("조회되지 않는 의뢰 ID가 있으면 서버 오류로 처리한다")
    void rejectsMissingJob() {
        when(jobRepository.findAllById(List.of(42L, 41L))).thenReturn(List.of(Job.builder().id(42L).build()));

        assertThatThrownBy(() -> jobService.getJobsByIds(List.of(42L, 41L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Test
    @DisplayName("지원서 ID 목록을 한 번에 조회해 ID별 지원서를 반환한다")
    void returnsJobApplicationsById() {
        JobApplication first = JobApplication.builder().id(21L).build();
        JobApplication second = JobApplication.builder().id(22L).build();
        when(jobApplicationRepository.findAllById(List.of(22L, 21L))).thenReturn(List.of(first, second));

        Map<Long, JobApplication> applications = jobService.getJobApplicationsByIds(List.of(22L, 21L));

        assertThat(applications).containsOnly(Map.entry(21L, first), Map.entry(22L, second));
    }

    @Test
    @DisplayName("조회되지 않는 지원서 ID가 있으면 서버 오류로 처리한다")
    void rejectsMissingJobApplication() {
        when(jobApplicationRepository.findAllById(List.of(22L, 21L)))
                .thenReturn(List.of(JobApplication.builder().id(21L).build()));

        assertThatThrownBy(() -> jobService.getJobApplicationsByIds(List.of(22L, 21L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Test
    @DisplayName("학생 프로필 ID로 본인 지원서를 조회해 ID별 지원서를 반환하고 없으면 빈 맵을 반환한다")
    void returnsJobApplicationsOfStudent() {
        JobApplication first = JobApplication.builder().id(21L).studentProfileId(7L).build();
        JobApplication second = JobApplication.builder().id(22L).studentProfileId(7L).build();
        when(jobApplicationRepository.findByStudentProfileId(7L)).thenReturn(List.of(first, second));
        when(jobApplicationRepository.findByStudentProfileId(8L)).thenReturn(List.of());

        assertThat(jobService.getJobApplicationsByStudentProfileId(7L))
                .containsOnly(Map.entry(21L, first), Map.entry(22L, second));
        assertThat(jobService.getJobApplicationsByStudentProfileId(8L)).isEmpty();
    }
}
