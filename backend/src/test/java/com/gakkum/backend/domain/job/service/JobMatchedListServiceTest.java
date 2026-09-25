package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;

class JobMatchedListServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, jobSpecialtyRepository, jobApplicationRepository,
            jobSubmissionRepository, mock(SpecialtyService.class));

    @Test
    @DisplayName("해당 사업주의 MATCHED 의뢰를 최신순으로 조회하고 대기 중 제출물만 연결한다")
    void returnsMatchedJobsWithPendingSubmissions() {
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.MATCHED))
                .thenReturn(List.of(job(44L), job(43L), job(42L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(44L, 43L, 42L))).thenReturn(List.of(
                JobSpecialty.create(42L, 12L),
                JobSpecialty.create(43L, 21L),
                JobSpecialty.create(44L, 22L)));
        when(jobSubmissionRepository.findByJobIdInAndReviewStatus(
                List.of(44L, 43L, 42L), JobSubmissionReviewStatus.PENDING)).thenReturn(List.of(
                submission(81L, 42L, JobSubmissionType.DRAFT),
                submission(87L, 43L, JobSubmissionType.REVISION)));

        List<MatchedJobData> result = jobService.getMatchedJobs(GetMatchedJobsCommand.of(5L));

        assertThat(result).extracting(data -> data.getJob().getId()).containsExactly(44L, 43L, 42L);
        assertThat(result.get(0).getPendingSubmission()).isNull();
        assertThat(result.get(1).getPendingSubmission().getSubmissionType()).isEqualTo(JobSubmissionType.REVISION);
        assertThat(result.get(2).getPendingSubmission().getId()).isEqualTo(81L);
        assertThat(result.get(2).getSpecialtyIds()).containsExactly(12L);
        verify(jobRepository).findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.MATCHED);
        verify(jobSubmissionRepository).findByJobIdInAndReviewStatus(
                List.of(44L, 43L, 42L), JobSubmissionReviewStatus.PENDING);
        verifyNoInteractions(jobApplicationRepository);
    }

    @Test
    @DisplayName("MATCHED 의뢰가 없으면 빈 목록을 반환하고 연관 데이터를 조회하지 않는다")
    void returnsEmptyListWithoutRelatedQueries() {
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.MATCHED))
                .thenReturn(List.of());

        assertThat(jobService.getMatchedJobs(GetMatchedJobsCommand.of(5L))).isEmpty();
        verifyNoInteractions(jobSpecialtyRepository, jobSubmissionRepository);
    }

    private Job job(Long id) {
        return Job.builder().id(id).ownerProfileId(5L).status(JobStatus.MATCHED).build();
    }

    private JobSubmission submission(Long id, Long jobId, JobSubmissionType type) {
        return JobSubmission.builder()
                .id(id)
                .jobId(jobId)
                .submissionType(type)
                .reviewStatus(JobSubmissionReviewStatus.PENDING)
                .build();
    }
}
