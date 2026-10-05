package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetStudentAppliedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentAppliedJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;

class JobStudentAppliedListServiceTest {

    private static final String DEMO_SESSION_ID = "01K58M6PJV8VAJMXHBHJ2DEMO1";

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, jobSpecialtyRepository, jobApplicationRepository,
            jobSubmissionRepository, Clock.systemUTC());

    @Test
    @DisplayName("대기 중 지원서의 정렬 순서를 유지하며 조회된 모집 중 의뢰와 연결하고, 조회되지 않은 의뢰의 지원서는 뺀다")
    void linksJobsKeepingApplicationOrder() {
        when(jobApplicationRepository.findByStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                7L, JobApplicationStatus.PENDING))
                .thenReturn(List.of(
                        application(204L, 42L), application(203L, 45L), application(202L, 44L), application(201L, 43L)));
        // 45번은 모집 중이 아니거나 다른 격리 범위이거나 없는 의뢰다. 의뢰 조회 결과 순서는 지원서 순서와 다르다
        when(jobRepository.findByIdInAndStatusAndDemoSessionId(List.of(42L, 45L, 44L, 43L), JobStatus.OPEN, null))
                .thenReturn(List.of(job(43L), job(44L), job(42L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L, 44L, 43L))).thenReturn(List.of(
                JobSpecialty.create(43L, 21L),
                JobSpecialty.create(42L, 12L),
                JobSpecialty.create(42L, 11L)));

        List<StudentAppliedJobData> result = jobService.getStudentAppliedJobs(
                GetStudentAppliedJobsCommand.of(7L, null));

        assertThat(result).extracting(data -> data.getApplication().getId()).containsExactly(204L, 202L, 201L);
        assertThat(result).extracting(data -> data.getJob().getId()).containsExactly(42L, 44L, 43L);
        assertThat(result.get(0).getSpecialtyIds()).containsExactly(12L, 11L);
        assertThat(result.get(1).getSpecialtyIds()).isEmpty();
        assertThat(result.get(2).getSpecialtyIds()).containsExactly(21L);
    }

    @Test
    @DisplayName("항목 수와 무관하게 지원서·의뢰·전문분야를 한 번씩만 조회한다")
    void readsEachSourceOnce() {
        when(jobApplicationRepository.findByStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                7L, JobApplicationStatus.PENDING))
                .thenReturn(List.of(application(203L, 44L), application(202L, 43L), application(201L, 42L)));
        when(jobRepository.findByIdInAndStatusAndDemoSessionId(List.of(44L, 43L, 42L), JobStatus.OPEN, null))
                .thenReturn(List.of(job(42L), job(43L), job(44L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(44L, 43L, 42L))).thenReturn(List.of());

        assertThat(jobService.getStudentAppliedJobs(GetStudentAppliedJobsCommand.of(7L, null))).hasSize(3);

        verify(jobApplicationRepository, times(1)).findByStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                7L, JobApplicationStatus.PENDING);
        verify(jobRepository, times(1)).findByIdInAndStatusAndDemoSessionId(any(), any(), any());
        verify(jobSpecialtyRepository, times(1)).findByJobIdIn(any());
        verifyNoMoreInteractions(jobApplicationRepository, jobRepository, jobSpecialtyRepository);
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("의뢰는 학생의 데모 격리 범위와 모집 중 상태로만 조회한다")
    void readsOpenJobsInDemoSession() {
        when(jobApplicationRepository.findByStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                7L, JobApplicationStatus.PENDING))
                .thenReturn(List.of(application(201L, 42L)));
        when(jobRepository.findByIdInAndStatusAndDemoSessionId(List.of(42L), JobStatus.OPEN, DEMO_SESSION_ID))
                .thenReturn(List.of(job(42L)));

        List<StudentAppliedJobData> result = jobService.getStudentAppliedJobs(
                GetStudentAppliedJobsCommand.of(7L, DEMO_SESSION_ID));

        assertThat(result).extracting(data -> data.getJob().getId()).containsExactly(42L);
        verify(jobRepository).findByIdInAndStatusAndDemoSessionId(List.of(42L), JobStatus.OPEN, DEMO_SESSION_ID);
    }

    @Test
    @DisplayName("대기 중 지원서가 없으면 빈 목록을 반환하고 의뢰·전문분야를 조회하지 않는다")
    void returnsEmptyListWithoutApplications() {
        when(jobApplicationRepository.findByStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                7L, JobApplicationStatus.PENDING))
                .thenReturn(List.of());

        assertThat(jobService.getStudentAppliedJobs(GetStudentAppliedJobsCommand.of(7L, null))).isEmpty();
        verifyNoInteractions(jobRepository, jobSpecialtyRepository);
    }

    @Test
    @DisplayName("지원한 의뢰가 모두 조회 조건에 맞지 않으면 빈 목록을 반환하고 전문분야를 조회하지 않는다")
    void returnsEmptyListWithoutListedJobs() {
        when(jobApplicationRepository.findByStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                7L, JobApplicationStatus.PENDING))
                .thenReturn(List.of(application(201L, 42L)));
        when(jobRepository.findByIdInAndStatusAndDemoSessionId(List.of(42L), JobStatus.OPEN, null))
                .thenReturn(List.of());

        assertThat(jobService.getStudentAppliedJobs(GetStudentAppliedJobsCommand.of(7L, null))).isEmpty();
        verifyNoInteractions(jobSpecialtyRepository);
    }

    private Job job(Long id) {
        return Job.builder().id(id).status(JobStatus.OPEN).build();
    }

    private JobApplication application(Long id, Long jobId) {
        return JobApplication.builder()
                .id(id)
                .jobId(jobId)
                .studentProfileId(7L)
                .status(JobApplicationStatus.PENDING)
                .build();
    }
}
