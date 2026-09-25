package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetOpenJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;

class JobOpenListServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, jobSpecialtyRepository, jobApplicationRepository,
            mock(JobSubmissionRepository.class), mock(SpecialtyService.class));

    @Test
    @DisplayName("해당 사업주의 OPEN 의뢰와 특기 ID, 대기 중 지원 수를 조회한다")
    void returnsOpenJobsWithSpecialtyIdsAndPendingCounts() {
        Job newer = job(42L, "웹사이트 제작");
        Job older = job(41L, "포스터 제작");
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.OPEN))
                .thenReturn(List.of(newer, older));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L, 41L))).thenReturn(List.of(
                JobSpecialty.create(42L, 12L),
                JobSpecialty.create(41L, 21L),
                JobSpecialty.create(42L, 11L)));
        when(jobApplicationRepository.findByJobIdInAndStatus(List.of(42L, 41L), JobApplicationStatus.PENDING))
                .thenReturn(List.of(
                        application(42L), application(42L), application(42L)));

        List<OpenJobData> result = jobService.getOpenJobs(GetOpenJobsCommand.of(5L));

        assertThat(result).extracting(data -> data.getJob().getId()).containsExactly(42L, 41L);
        assertThat(result.get(0).getSpecialtyIds()).containsExactly(12L, 11L);
        assertThat(result.get(1).getSpecialtyIds()).containsExactly(21L);
        assertThat(result.get(0).getApplicantCount()).isEqualTo(3);
        assertThat(result.get(1).getApplicantCount()).isZero();
        verify(jobRepository).findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.OPEN);
        verify(jobApplicationRepository).findByJobIdInAndStatus(List.of(42L, 41L), JobApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("OPEN 의뢰가 없으면 빈 목록을 반환하고 연관 테이블을 조회하지 않는다")
    void returnsEmptyListWithoutRelatedQueries() {
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.OPEN))
                .thenReturn(List.of());

        assertThat(jobService.getOpenJobs(GetOpenJobsCommand.of(5L))).isEmpty();
        verifyNoInteractions(jobSpecialtyRepository, jobApplicationRepository);
    }

    private Job job(Long id, String title) {
        return Job.builder()
                .id(id)
                .ownerProfileId(5L)
                .title(title)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .revisionCount(2)
                .status(JobStatus.OPEN)
                .build();
    }

    private JobApplication application(Long jobId) {
        return JobApplication.builder()
                .jobId(jobId)
                .status(JobApplicationStatus.PENDING)
                .build();
    }
}
