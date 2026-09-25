package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetClosedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobClosedListServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, jobSpecialtyRepository, jobApplicationRepository,
            jobSubmissionRepository, mock(SpecialtyService.class));

    @Test
    @DisplayName("사업주의 CLOSED 의뢰를 완료 시각과 ID 최신순으로 조회하고 특기를 일괄 연결한다")
    void returnsClosedJobsWithSpecialties() {
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCompletedAtDescIdDesc(5L, JobStatus.CLOSED))
                .thenReturn(List.of(
                        job(44L, LocalDateTime.of(2026, 9, 27, 10, 0)),
                        job(43L, LocalDateTime.of(2026, 9, 25, 10, 0)),
                        job(42L, LocalDateTime.of(2026, 9, 25, 10, 0))));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(44L, 43L, 42L))).thenReturn(List.of(
                JobSpecialty.create(42L, 12L),
                JobSpecialty.create(42L, 11L),
                JobSpecialty.create(44L, 21L)));

        List<ClosedJobData> result = jobService.getClosedJobs(GetClosedJobsCommand.of(5L));

        assertThat(result).extracting(data -> data.getJob().getId()).containsExactly(44L, 43L, 42L);
        assertThat(result.get(0).getSpecialtyIds()).containsExactly(21L);
        assertThat(result.get(1).getSpecialtyIds()).isEmpty();
        assertThat(result.get(2).getSpecialtyIds()).containsExactly(12L, 11L);
        verify(jobRepository).findByOwnerProfileIdAndStatusOrderByCompletedAtDescIdDesc(5L, JobStatus.CLOSED);
        verify(jobSpecialtyRepository).findByJobIdIn(List.of(44L, 43L, 42L));
        verifyNoInteractions(jobApplicationRepository, jobSubmissionRepository);
    }

    @Test
    @DisplayName("CLOSED 의뢰가 없으면 빈 목록을 반환하고 특기를 조회하지 않는다")
    void returnsEmptyList() {
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCompletedAtDescIdDesc(5L, JobStatus.CLOSED))
                .thenReturn(List.of());

        assertThat(jobService.getClosedJobs(GetClosedJobsCommand.of(5L))).isEmpty();
        verifyNoInteractions(jobSpecialtyRepository, jobApplicationRepository, jobSubmissionRepository);
    }

    @Test
    @DisplayName("CLOSED 의뢰에 완료 시각이 없으면 조회를 실패시킨다")
    void rejectsMissingCompletionDate() {
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCompletedAtDescIdDesc(5L, JobStatus.CLOSED))
                .thenReturn(List.of(job(42L, null)));

        assertThatThrownBy(() -> jobService.getClosedJobs(GetClosedJobsCommand.of(5L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
        verifyNoInteractions(jobSpecialtyRepository);
    }

    private Job job(Long id, LocalDateTime completedAt) {
        return Job.builder()
                .id(id)
                .ownerProfileId(5L)
                .status(JobStatus.CLOSED)
                .completedAt(completedAt)
                .build();
    }
}
