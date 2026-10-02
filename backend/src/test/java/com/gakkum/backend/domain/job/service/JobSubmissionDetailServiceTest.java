package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionDetailData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobSubmissionDetailServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, mock(JobSpecialtyRepository.class), mock(JobApplicationRepository.class),
            jobSubmissionRepository, Clock.systemUTC());

    @Test
    @DisplayName("본인 의뢰의 검토 대기 제출물을 의뢰와 함께 반환한다")
    void returnsPendingSubmission() {
        JobSubmission submission = JobSubmission.create(
                42L, JobSubmissionType.REVISION, 1, List.of("https://example.com/a.pdf"), "수정했습니다.");
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(job(7L)));
        when(jobSubmissionRepository.findByJobIdAndReviewStatus(42L, JobSubmissionReviewStatus.PENDING))
                .thenReturn(Optional.of(submission));

        JobSubmissionDetailData data = jobService.getPendingSubmission(GetJobSubmissionCommand.of(42L, 5L));

        assertThat(data.getJob().getId()).isEqualTo(42L);
        assertThat(data.getSubmission()).isSameAs(submission);
    }

    @Test
    @DisplayName("존재하지 않거나 다른 사장님의 의뢰는 JOB_404로 거부하고 제출물을 조회하지 않는다")
    void rejectsJobNotOwned() {
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getPendingSubmission(GetJobSubmissionCommand.of(42L, 5L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOB_NOT_FOUND));
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("검토 대기 제출물이 없으면 JOB_SUBMISSION_404로 거부한다")
    void rejectsMissingPendingSubmission() {
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(job(7L)));
        when(jobSubmissionRepository.findByJobIdAndReviewStatus(42L, JobSubmissionReviewStatus.PENDING))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getPendingSubmission(GetJobSubmissionCommand.of(42L, 5L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOB_SUBMISSION_NOT_FOUND));
    }

    @Test
    @DisplayName("제출물이 있는데 선택된 학생이 없으면 공통 500으로 실패한다")
    void rejectsMissingSelectedStudent() {
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(job(null)));
        when(jobSubmissionRepository.findByJobIdAndReviewStatus(42L, JobSubmissionReviewStatus.PENDING))
                .thenReturn(Optional.of(JobSubmission.create(
                        42L, JobSubmissionType.DRAFT, 0, List.of("https://example.com/a.pdf"), "초안")));

        assertThatThrownBy(() -> jobService.getPendingSubmission(GetJobSubmissionCommand.of(42L, 5L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private Job job(Long selectedStudentProfileId) {
        return Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .status(JobStatus.MATCHED)
                .selectedStudentProfileId(selectedStudentProfileId)
                .build();
    }
}
