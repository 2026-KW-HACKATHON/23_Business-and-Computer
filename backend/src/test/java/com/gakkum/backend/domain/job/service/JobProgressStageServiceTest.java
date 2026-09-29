package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetStudentMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;

class JobProgressStageServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, jobSpecialtyRepository, mock(JobApplicationRepository.class),
            jobSubmissionRepository, Clock.systemUTC());

    @Test
    @DisplayName("상세 조회는 OPEN이면 의뢰, CLOSED면 완료이고 제출물을 조회하지 않는다")
    void detailForOpenAndClosedJobsDoesNotReadSubmissions() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job(1L, JobStatus.OPEN)));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, JobStatus.CLOSED)));

        assertThat(jobService.getJobDetail(1L).getProgressStage()).isEqualTo(JobProgressStage.REQUESTED);
        assertThat(jobService.getJobDetail(2L).getProgressStage()).isEqualTo(JobProgressStage.COMPLETED);
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("상세 조회는 MATCHED 의뢰의 최신 제출물로 시작·초안·수정 단계를 계산한다")
    void detailForMatchedJobUsesLatestSubmission() {
        when(jobRepository.findById(3L)).thenReturn(Optional.of(job(3L, JobStatus.MATCHED)));
        when(jobSubmissionRepository.findFirstByJobIdOrderByRevisionNumberDesc(3L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(submission(3L, JobSubmissionType.DRAFT, JobSubmissionReviewStatus.PENDING)))
                .thenReturn(Optional.of(
                        submission(3L, JobSubmissionType.DRAFT, JobSubmissionReviewStatus.REVISION_REQUESTED)))
                .thenReturn(Optional.of(submission(3L, JobSubmissionType.REVISION, JobSubmissionReviewStatus.PENDING)));

        assertThat(jobService.getJobDetail(3L).getProgressStage()).isEqualTo(JobProgressStage.STARTED);
        assertThat(jobService.getJobDetail(3L).getProgressStage()).isEqualTo(JobProgressStage.DRAFT);
        assertThat(jobService.getJobDetail(3L).getProgressStage()).isEqualTo(JobProgressStage.REVISION);
        assertThat(jobService.getJobDetail(3L).getProgressStage()).isEqualTo(JobProgressStage.REVISION);
    }

    @Test
    @DisplayName("사장님 진행 목록은 수정 요청 후 검토 대기 제출물이 없어도 수정 단계이고 pendingSubmission은 비운다")
    void ownerMatchedListShowsRevisionWithoutPendingSubmission() {
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.MATCHED))
                .thenReturn(List.of(job(42L, JobStatus.MATCHED)));
        when(jobSubmissionRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of(
                submission(42L, JobSubmissionType.DRAFT, JobSubmissionReviewStatus.REVISION_REQUESTED)));

        List<MatchedJobData> result = jobService.getMatchedJobs(GetMatchedJobsCommand.of(5L));

        assertThat(result.get(0).getPendingSubmission()).isNull();
        assertThat(result.get(0).getProgressStage()).isEqualTo(JobProgressStage.REVISION);
    }

    @Test
    @DisplayName("학생 진행 목록도 최신 제출물 기준으로 단계를 계산한다")
    void studentMatchedListUsesLatestSubmission() {
        when(jobRepository.findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(7L, JobStatus.MATCHED))
                .thenReturn(List.of(job(42L, JobStatus.MATCHED)));
        JobSubmission draft = submission(42L, JobSubmissionType.DRAFT, JobSubmissionReviewStatus.REVISION_REQUESTED);
        JobSubmission revision = JobSubmission.builder()
                .jobId(42L).submissionType(JobSubmissionType.REVISION).revisionNumber(1)
                .reviewStatus(JobSubmissionReviewStatus.PENDING).build();
        when(jobSubmissionRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of(revision, draft));

        assertThat(jobService.getStudentMatchedJobs(GetStudentMatchedJobsCommand.of(7L)).get(0).getProgressStage())
                .isEqualTo(JobProgressStage.REVISION);
    }

    private Job job(Long id, JobStatus status) {
        return Job.builder().id(id).ownerProfileId(5L).status(status).build();
    }

    private JobSubmission submission(Long jobId, JobSubmissionType type, JobSubmissionReviewStatus status) {
        return JobSubmission.builder()
                .jobId(jobId).submissionType(type).revisionNumber(0).reviewStatus(status).build();
    }
}
