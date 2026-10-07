package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
    private static final Long STUDENT = 7L;
    private static final Long OTHER_STUDENT = 9L;
    private static final List<JobApplicationStatus> LISTED_STATUSES =
            List.of(JobApplicationStatus.PENDING, JobApplicationStatus.REJECTED);

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, jobSpecialtyRepository, jobApplicationRepository,
            jobSubmissionRepository, Clock.systemUTC());

    @Test
    @DisplayName("지원서의 정렬 순서를 유지하며 조회된 의뢰와 연결하고, 조회되지 않은 의뢰의 지원서는 뺀다")
    void linksJobsKeepingApplicationOrder() {
        givenApplications(
                pending(204L, 42L), pending(203L, 45L), pending(202L, 44L), pending(201L, 43L));
        // 45번은 다른 격리 범위이거나 없는 의뢰다. 의뢰 조회 결과 순서는 지원서 순서와 다르다
        when(jobRepository.findByIdInAndDemoSessionId(List.of(42L, 45L, 44L, 43L), null))
                .thenReturn(List.of(openJob(43L), openJob(44L), openJob(42L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L, 44L, 43L))).thenReturn(List.of(
                JobSpecialty.create(43L, 21L),
                JobSpecialty.create(42L, 12L),
                JobSpecialty.create(42L, 11L)));

        List<StudentAppliedJobData> result = jobService.getStudentAppliedJobs(
                GetStudentAppliedJobsCommand.of(STUDENT, null));

        assertThat(result).extracting(data -> data.getApplication().getId()).containsExactly(204L, 202L, 201L);
        assertThat(result).extracting(data -> data.getJob().getId()).containsExactly(42L, 44L, 43L);
        assertThat(result).extracting(StudentAppliedJobData::getApplicationStatus)
                .containsOnly(JobApplicationStatus.PENDING);
        assertThat(result.get(0).getSpecialtyIds()).containsExactly(12L, 11L);
        assertThat(result.get(1).getSpecialtyIds()).isEmpty();
        assertThat(result.get(2).getSpecialtyIds()).containsExactly(21L);
    }

    @Test
    @DisplayName("다른 학생이 선정된 의뢰의 대기 중 지원서는 의뢰 상태와 무관하게 탈락으로 계산하고 저장된 상태는 바꾸지 않는다")
    void computesRejectedWhenAnotherStudentIsSelected() {
        givenApplications(pending(204L, 44L), pending(203L, 43L), pending(202L, 42L), pending(201L, 41L));
        when(jobRepository.findByIdInAndDemoSessionId(List.of(44L, 43L, 42L, 41L), null)).thenReturn(List.of(
                job(44L, JobStatus.AWAITING_START, OTHER_STUDENT),
                job(43L, JobStatus.MATCHED, OTHER_STUDENT),
                job(42L, JobStatus.CLOSED, OTHER_STUDENT),
                job(41L, JobStatus.CANCELLED, OTHER_STUDENT)));

        List<StudentAppliedJobData> result = jobService.getStudentAppliedJobs(
                GetStudentAppliedJobsCommand.of(STUDENT, null));

        assertThat(result).extracting(data -> data.getApplication().getId(),
                        StudentAppliedJobData::getApplicationStatus, data -> data.getJob().getStatus())
                .containsExactly(
                        tuple(204L, JobApplicationStatus.REJECTED, JobStatus.AWAITING_START),
                        tuple(203L, JobApplicationStatus.REJECTED, JobStatus.MATCHED),
                        tuple(202L, JobApplicationStatus.REJECTED, JobStatus.CLOSED),
                        tuple(201L, JobApplicationStatus.REJECTED, JobStatus.CANCELLED));
        assertThat(result).extracting(data -> data.getApplication().getStatus())
                .containsOnly(JobApplicationStatus.PENDING);
        verify(jobApplicationRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("저장된 탈락 지원서는 의뢰 상태와 선정 여부에 무관하게 탈락으로 포함한다")
    void includesStoredRejectedRegardlessOfJobStatus() {
        givenApplications(rejected(205L, 45L), rejected(204L, 44L), rejected(203L, 43L), rejected(202L, 42L),
                rejected(201L, 41L));
        when(jobRepository.findByIdInAndDemoSessionId(List.of(45L, 44L, 43L, 42L, 41L), null)).thenReturn(List.of(
                openJob(45L),
                job(44L, JobStatus.MATCHED, OTHER_STUDENT),
                job(43L, JobStatus.CLOSED, OTHER_STUDENT),
                job(42L, JobStatus.CANCELLED, OTHER_STUDENT),
                job(41L, JobStatus.CANCELLED, null)));

        List<StudentAppliedJobData> result = jobService.getStudentAppliedJobs(
                GetStudentAppliedJobsCommand.of(STUDENT, null));

        assertThat(result).extracting(data -> data.getApplication().getId())
                .containsExactly(205L, 204L, 203L, 202L, 201L);
        assertThat(result).extracting(StudentAppliedJobData::getApplicationStatus)
                .containsOnly(JobApplicationStatus.REJECTED);
    }

    @Test
    @DisplayName("본인이 선정된 의뢰와 학생 선정 없이 모집이 끝난 의뢰의 대기 중 지원서는 뺀다")
    void excludesSelectedAndCancelledBeforeSelection() {
        givenApplications(pending(204L, 44L), pending(203L, 43L), pending(202L, 42L), pending(201L, 41L));
        when(jobRepository.findByIdInAndDemoSessionId(List.of(44L, 43L, 42L, 41L), null)).thenReturn(List.of(
                // 본인이 선정됐지만 지원서가 대기 중으로 남은 의뢰
                job(44L, JobStatus.MATCHED, STUDENT),
                job(43L, JobStatus.CLOSED, STUDENT),
                // 학생 선정 없이 취소된 의뢰
                job(42L, JobStatus.CANCELLED, null),
                openJob(41L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(41L))).thenReturn(List.of());

        List<StudentAppliedJobData> result = jobService.getStudentAppliedJobs(
                GetStudentAppliedJobsCommand.of(STUDENT, null));

        assertThat(result).extracting(data -> data.getApplication().getId()).containsExactly(201L);
        assertThat(result.get(0).getApplicationStatus()).isEqualTo(JobApplicationStatus.PENDING);
        // 뺀 지원서의 의뢰는 전문분야를 조회하지 않는다
        verify(jobSpecialtyRepository).findByJobIdIn(List.of(41L));
    }

    @Test
    @DisplayName("선정된 지원서는 조회하지 않고 대기·탈락 지원서만 조회한다")
    void readsPendingAndRejectedApplicationsOnly() {
        givenApplications();

        jobService.getStudentAppliedJobs(GetStudentAppliedJobsCommand.of(STUDENT, null));

        verify(jobApplicationRepository).findByStudentProfileIdAndStatusInOrderByCreatedAtDescIdDesc(
                STUDENT, LISTED_STATUSES);
        verifyNoMoreInteractions(jobApplicationRepository);
    }

    @Test
    @DisplayName("항목 수와 무관하게 지원서·의뢰·전문분야를 한 번씩만 조회한다")
    void readsEachSourceOnce() {
        givenApplications(pending(204L, 45L), rejected(203L, 44L), pending(202L, 43L), pending(201L, 42L));
        when(jobRepository.findByIdInAndDemoSessionId(List.of(45L, 44L, 43L, 42L), null)).thenReturn(List.of(
                openJob(42L), job(43L, JobStatus.MATCHED, OTHER_STUDENT), openJob(44L), openJob(45L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(45L, 44L, 43L, 42L))).thenReturn(List.of());

        assertThat(jobService.getStudentAppliedJobs(GetStudentAppliedJobsCommand.of(STUDENT, null))).hasSize(4);

        verify(jobApplicationRepository, times(1)).findByStudentProfileIdAndStatusInOrderByCreatedAtDescIdDesc(
                STUDENT, LISTED_STATUSES);
        verify(jobRepository, times(1)).findByIdInAndDemoSessionId(any(), any());
        verify(jobSpecialtyRepository, times(1)).findByJobIdIn(any());
        verifyNoMoreInteractions(jobApplicationRepository, jobRepository, jobSpecialtyRepository);
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("의뢰는 상태 제한 없이 학생의 데모 격리 범위로만 조회한다")
    void readsJobsInDemoSession() {
        givenApplications(pending(201L, 42L));
        when(jobRepository.findByIdInAndDemoSessionId(List.of(42L), DEMO_SESSION_ID))
                .thenReturn(List.of(openJob(42L)));

        List<StudentAppliedJobData> result = jobService.getStudentAppliedJobs(
                GetStudentAppliedJobsCommand.of(STUDENT, DEMO_SESSION_ID));

        assertThat(result).extracting(data -> data.getJob().getId()).containsExactly(42L);
        verify(jobRepository).findByIdInAndDemoSessionId(List.of(42L), DEMO_SESSION_ID);
    }

    @Test
    @DisplayName("대기·탈락 지원서가 없으면 빈 목록을 반환하고 의뢰·전문분야를 조회하지 않는다")
    void returnsEmptyListWithoutApplications() {
        givenApplications();

        assertThat(jobService.getStudentAppliedJobs(GetStudentAppliedJobsCommand.of(STUDENT, null))).isEmpty();
        verifyNoInteractions(jobRepository, jobSpecialtyRepository);
    }

    @Test
    @DisplayName("지원한 의뢰가 모두 조회 조건에 맞지 않으면 빈 목록을 반환하고 전문분야를 조회하지 않는다")
    void returnsEmptyListWithoutListedJobs() {
        givenApplications(pending(202L, 43L), pending(201L, 42L));
        // 43번은 조회되지 않고 42번은 학생 선정 없이 취소됐다
        when(jobRepository.findByIdInAndDemoSessionId(List.of(43L, 42L), null))
                .thenReturn(List.of(job(42L, JobStatus.CANCELLED, null)));

        assertThat(jobService.getStudentAppliedJobs(GetStudentAppliedJobsCommand.of(STUDENT, null))).isEmpty();
        verifyNoInteractions(jobSpecialtyRepository);
    }

    private void givenApplications(JobApplication... applications) {
        when(jobApplicationRepository.findByStudentProfileIdAndStatusInOrderByCreatedAtDescIdDesc(
                STUDENT, LISTED_STATUSES))
                .thenReturn(List.of(applications));
    }

    private Job openJob(Long id) {
        return job(id, JobStatus.OPEN, null);
    }

    private Job job(Long id, JobStatus status, Long selectedStudentProfileId) {
        return Job.builder().id(id).status(status).selectedStudentProfileId(selectedStudentProfileId).build();
    }

    private JobApplication pending(Long id, Long jobId) {
        return application(id, jobId, JobApplicationStatus.PENDING);
    }

    private JobApplication rejected(Long id, Long jobId) {
        return application(id, jobId, JobApplicationStatus.REJECTED);
    }

    private JobApplication application(Long id, Long jobId, JobApplicationStatus status) {
        return JobApplication.builder()
                .id(id)
                .jobId(jobId)
                .studentProfileId(STUDENT)
                .status(status)
                .build();
    }
}
