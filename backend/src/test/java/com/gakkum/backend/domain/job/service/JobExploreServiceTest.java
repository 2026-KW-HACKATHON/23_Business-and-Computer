package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetExploreJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ExploreJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;

class JobExploreServiceTest {

    private static final LocalDateTime BOUND = LocalDateTime.of(2026, 9, 30, 10, 0);

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(jobRepository, jobSpecialtyRepository,
            mock(JobApplicationRepository.class), jobSubmissionRepository, Clock.systemUTC());

    @Test
    @DisplayName("취소를 뺀 탐색 의뢰에 소분류 ID와 상태별 진행 단계를 붙이고 최신 제출물은 진행 중 의뢰만 조회한다")
    void returnsExploreJobsWithProgressStages() {
        when(jobRepository.findExploreLatestInCategory(JobStatus.CANCELLED, 3L, BOUND, 50L, Limit.of(21)))
                .thenReturn(List.of(
                        job(45L, JobStatus.OPEN),
                        job(44L, JobStatus.MATCHED),
                        job(43L, JobStatus.MATCHED),
                        job(42L, JobStatus.MATCHED),
                        job(41L, JobStatus.CLOSED)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(45L, 44L, 43L, 42L, 41L))).thenReturn(List.of(
                JobSpecialty.create(45L, 11L),
                JobSpecialty.create(44L, 3L),
                JobSpecialty.create(45L, 4L)));
        when(jobSubmissionRepository.findByJobIdIn(List.of(44L, 43L, 42L))).thenReturn(List.of(
                submission(43L, JobSubmissionType.DRAFT, 1, JobSubmissionReviewStatus.PENDING),
                submission(42L, JobSubmissionType.DRAFT, 1, JobSubmissionReviewStatus.REVISION_REQUESTED),
                submission(42L, JobSubmissionType.REVISION, 2, JobSubmissionReviewStatus.PENDING)));

        List<ExploreJobData> result = jobService.getExploreJobs(GetExploreJobsCommand.of(3L, false, BOUND, 50L, 21));

        assertThat(result).extracting(data -> data.getJob().getId(), ExploreJobData::getProgressStage)
                .containsExactly(
                        tuple(45L, JobProgressStage.REQUESTED),
                        tuple(44L, JobProgressStage.STARTED),
                        tuple(43L, JobProgressStage.DRAFT),
                        tuple(42L, JobProgressStage.REVISION),
                        tuple(41L, JobProgressStage.COMPLETED));
        assertThat(result.get(0).getSpecialtyIds()).containsExactly(11L, 4L);
        assertThat(result.get(4).getSpecialtyIds()).isEmpty();
        verify(jobRepository, never()).findByStatusNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(any(), any(), any());
    }

    @Test
    @DisplayName("대분류 없는 오래된순은 경계 시각과 같은 행 뒤에 경계 이후 행을 이어 붙이고, 진행 중 의뢰가 없으면 제출물을 조회하지 않는다")
    void usesOldestQueryWithoutSubmissionLookup() {
        when(jobRepository.findByStatusNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                JobStatus.CANCELLED, BOUND, 50L, Limit.of(2))).thenReturn(List.of(job(41L, JobStatus.OPEN)));
        when(jobRepository.findByStatusNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                JobStatus.CANCELLED, BOUND, Limit.of(1))).thenReturn(List.of(job(42L, JobStatus.CLOSED)));

        List<ExploreJobData> result = jobService.getExploreJobs(GetExploreJobsCommand.of(null, true, BOUND, 50L, 2));

        assertThat(result).extracting(data -> data.getJob().getId(), ExploreJobData::getProgressStage)
                .containsExactly(tuple(41L, JobProgressStage.REQUESTED), tuple(42L, JobProgressStage.COMPLETED));
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("대분류 없는 최신순은 경계 시각과 같은 행과 경계 이전 행을, 대분류 있는 오래된순은 분류 쿼리 하나를 쓴다")
    void dispatchesExploreQueryByOrderAndCategory() {
        jobService.getExploreJobs(GetExploreJobsCommand.of(null, false, BOUND, 50L, 2));
        jobService.getExploreJobs(GetExploreJobsCommand.of(4L, true, BOUND, 50L, 2));

        verify(jobRepository).findByStatusNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                JobStatus.CANCELLED, BOUND, 50L, Limit.of(2));
        verify(jobRepository).findByStatusNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                JobStatus.CANCELLED, BOUND, Limit.of(2));
        verify(jobRepository).findExploreOldestInCategory(JobStatus.CANCELLED, 4L, BOUND, 50L, Limit.of(2));
    }

    @Test
    @DisplayName("조회된 의뢰가 없으면 빈 목록을 반환하고 연관 테이블을 조회하지 않는다")
    void returnsEmptyWithoutRelatedQueries() {
        assertThat(jobService.getExploreJobs(GetExploreJobsCommand.of(null, false, BOUND, 50L, 2))).isEmpty();
        verifyNoInteractions(jobSpecialtyRepository, jobSubmissionRepository);
    }

    private Job job(Long id, JobStatus status) {
        return Job.builder().id(id).ownerProfileId(5L).status(status).build();
    }

    private JobSubmission submission(Long jobId, JobSubmissionType type, int revisionNumber,
            JobSubmissionReviewStatus reviewStatus) {
        return JobSubmission.builder()
                .jobId(jobId).submissionType(type).revisionNumber(revisionNumber).reviewStatus(reviewStatus).build();
    }
}
