package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobSubmissionCreateServiceTest {

    private static final String FILE_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/job-submissions/42/7/x/a.pdf";

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, mock(JobSpecialtyRepository.class), mock(JobApplicationRepository.class),
            jobSubmissionRepository, mock(SpecialtyService.class));

    @Test
    @DisplayName("매칭된 학생의 첫 초안을 수정 번호 0, 검토 대기 상태로 저장한다")
    void savesDraft() {
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job(JobStatus.MATCHED, 7L)));
        when(jobSubmissionRepository.saveAndFlush(any(JobSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JobSubmission saved = jobService.submitDraft(command(), 7L);

        assertThat(saved.getJobId()).isEqualTo(42L);
        assertThat(saved.getSubmissionType()).isEqualTo(JobSubmissionType.DRAFT);
        assertThat(saved.getRevisionNumber()).isZero();
        assertThat(saved.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
        assertThat(saved.getFileUrls()).containsExactly(FILE_URL);
        assertThat(saved.getMessage()).isEqualTo("초안입니다.");
    }

    @Test
    @DisplayName("초안 마감일이 지났어도 제출을 허용한다")
    void allowsDraftAfterDeadline() {
        Job job = Job.builder()
                .id(42L)
                .status(JobStatus.MATCHED)
                .selectedStudentProfileId(7L)
                .draftDeadline(LocalDate.of(2000, 1, 1))
                .build();
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job));
        when(jobSubmissionRepository.saveAndFlush(any(JobSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(jobService.submitDraft(command(), 7L).getSubmissionType()).isEqualTo(JobSubmissionType.DRAFT);
    }

    @Test
    @DisplayName("존재하지 않는 의뢰는 JOB_404로 거부한다")
    void rejectsMissingJob() {
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.empty());

        assertError(() -> jobService.submitDraft(command(), 7L), ErrorCode.JOB_NOT_FOUND);
    }

    @Test
    @DisplayName("매칭된 학생이 아니면 JOB_SUBMISSION_403으로 거부한다")
    void rejectsOtherStudent() {
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job(JobStatus.MATCHED, 7L)));

        assertError(() -> jobService.submitDraft(command(), 8L), ErrorCode.JOB_SUBMISSION_FORBIDDEN);
        verify(jobSubmissionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("의뢰가 MATCHED 상태가 아니면 JOB_SUBMISSION_409_STATUS로 거부한다")
    void rejectsJobNotMatched() {
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job(JobStatus.CLOSED, 7L)));

        assertError(() -> jobService.submitDraft(command(), 7L), ErrorCode.JOB_SUBMISSION_NOT_AVAILABLE);
        verify(jobSubmissionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미 제출물이 있으면 JOB_SUBMISSION_409_DUPLICATE로 거부한다")
    void rejectsDuplicateDraft() {
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job(JobStatus.MATCHED, 7L)));
        when(jobSubmissionRepository.existsByJobId(42L)).thenReturn(true);

        assertError(() -> jobService.submitDraft(command(), 7L), ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS);
        verify(jobSubmissionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동시 제출로 유니크 제약이 충돌하면 JOB_SUBMISSION_409_DUPLICATE로 바꿔 응답한다")
    void convertsUniqueViolationToDuplicate() {
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job(JobStatus.MATCHED, 7L)));
        when(jobSubmissionRepository.saveAndFlush(any(JobSubmission.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertError(() -> jobService.submitDraft(command(), 7L), ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("업로드 준비 가능 여부는 매칭 학생과 MATCHED 상태만 확인하고 기존 제출물은 보지 않는다")
    void submittableJobIgnoresExistingSubmission() {
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job(JobStatus.MATCHED, 7L)));
        when(jobSubmissionRepository.existsByJobId(42L)).thenReturn(true);

        assertThat(jobService.getSubmittableJob(42L, 7L).getId()).isEqualTo(42L);
        assertError(() -> jobService.getSubmittableJob(42L, 8L), ErrorCode.JOB_SUBMISSION_FORBIDDEN);
    }

    @Test
    @DisplayName("초안 사전 확인은 기존 제출물이 있으면 JOB_SUBMISSION_409_DUPLICATE로 거부한다")
    void draftPrecheckRejectsExistingSubmission() {
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job(JobStatus.MATCHED, 7L)));
        when(jobSubmissionRepository.existsByJobId(42L)).thenReturn(true);

        assertError(() -> jobService.validateDraftSubmittable(42L, 7L), ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS);
    }

    private CreateJobSubmissionCommand command() {
        return CreateJobSubmissionCommand.of("KAKAO_1", 42L, List.of(FILE_URL), "초안입니다.");
    }

    private Job job(JobStatus status, Long selectedStudentProfileId) {
        return Job.builder()
                .id(42L)
                .status(status)
                .selectedStudentProfileId(selectedStudentProfileId)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .build();
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
