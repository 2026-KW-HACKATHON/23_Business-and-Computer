package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Limit;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;

@DisplayName("미확인 제출물 자동 완료 (대상 조회 조건·잠금 후 재확인·완료 처리)")
class JobSubmissionAutoCompleteServiceTest {

    // 기준 시각은 스케줄러 실행 시작 시각, NOW는 그 뒤 실제로 처리한 시각이다
    private static final LocalDateTime REFERENCE_TIME = LocalDateTime.of(2026, 9, 28, 3, 0);
    private static final LocalDateTime EXPIRED_AT = REFERENCE_TIME.minusHours(168);
    private static final Instant NOW = Instant.parse("2026-09-28T03:15:30Z");

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, mock(JobSpecialtyRepository.class), mock(JobApplicationRepository.class),
            jobSubmissionRepository, Clock.fixed(NOW, ZoneId.of("UTC")));

    @Test
    @DisplayName("대상 조회는 진행 중 의뢰의 검토 대기 제출물을 기준 시각 168시간 전까지로 한정해 주어진 ID 뒤부터 읽는다")
    void queriesTargetsSubmittedUntilCutoff() {
        when(jobRepository.findAutoCompletableJobIds(100L, JobStatus.MATCHED, JobSubmissionReviewStatus.PENDING,
                EXPIRED_AT, Limit.of(100))).thenReturn(List.of(101L, 205L));

        assertThat(jobService.getAutoCompletableJobIds(100L, REFERENCE_TIME, 100)).containsExactly(101L, 205L);
    }

    @ParameterizedTest
    @EnumSource(JobSubmissionType.class)
    @DisplayName("초안·수정안이 제출 후 정확히 168시간이 되면 제출물은 APPROVED, 의뢰는 CLOSED가 되고 실제 처리 시각을 완료 시각으로 기록한다")
    void completesAtExactly168Hours(JobSubmissionType type) {
        Job job = givenJob(JobStatus.MATCHED, null);
        JobSubmission submission = givenLatestSubmission(type, JobSubmissionReviewStatus.PENDING, EXPIRED_AT);

        assertThat(jobService.autoCompleteSubmission(42L, REFERENCE_TIME)).containsSame(job);

        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.APPROVED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(job.getCompletedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(jobRepository).findLockedById(42L);
    }

    @ParameterizedTest
    @EnumSource(JobSubmissionType.class)
    @DisplayName("초안·수정안이 제출 후 168시간을 넘겼으면 완료한다")
    void completesAfter168Hours(JobSubmissionType type) {
        Job job = givenJob(JobStatus.MATCHED, null);
        givenLatestSubmission(type, JobSubmissionReviewStatus.PENDING, EXPIRED_AT.minusDays(30));

        assertThat(jobService.autoCompleteSubmission(42L, REFERENCE_TIME)).isPresent();
        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
    }

    @ParameterizedTest
    @EnumSource(JobSubmissionType.class)
    @DisplayName("초안·수정안이 제출 후 168시간 직전이면 의뢰와 제출물을 그대로 둔다")
    void keepsJustBefore168Hours(JobSubmissionType type) {
        Job job = givenJob(JobStatus.MATCHED, null);
        JobSubmission submission = givenLatestSubmission(type, JobSubmissionReviewStatus.PENDING,
                EXPIRED_AT.plus(1, ChronoUnit.MICROS));

        assertThat(jobService.autoCompleteSubmission(42L, REFERENCE_TIME)).isEmpty();

        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(job.getCompletedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = JobSubmissionReviewStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("잠근 뒤 최신 제출물이 수정 요청됐거나 이미 승인됐으면 기간이 지났어도 건너뛴다")
    void skipsReviewedSubmission(JobSubmissionReviewStatus reviewStatus) {
        Job job = givenJob(JobStatus.MATCHED, null);
        JobSubmission submission = givenLatestSubmission(JobSubmissionType.DRAFT, reviewStatus,
                EXPIRED_AT.minusDays(1));

        assertThat(jobService.autoCompleteSubmission(42L, REFERENCE_TIME)).isEmpty();

        assertThat(submission.getReviewStatus()).isEqualTo(reviewStatus);
        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = "MATCHED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("잠근 뒤 의뢰가 진행 중이 아니면(완료·취소 포함) 제출물을 읽지 않고 건너뛴다")
    void skipsJobNotInProgress(JobStatus status) {
        Job job = givenJob(status, null);

        assertThat(jobService.autoCompleteSubmission(42L, REFERENCE_TIME)).isEmpty();

        assertThat(job.getStatus()).isEqualTo(status);
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("데모 의뢰는 기간이 지났어도 건너뛴다")
    void skipsDemoJob() {
        Job job = givenJob(JobStatus.MATCHED, "01K58M6PJV8VAJMXHBHJ2DEMO1");
        givenLatestSubmission(JobSubmissionType.DRAFT, JobSubmissionReviewStatus.PENDING, EXPIRED_AT.minusDays(1));

        assertThat(jobService.autoCompleteSubmission(42L, REFERENCE_TIME)).isEmpty();

        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("제출물이 없는 의뢰와 사라진 의뢰는 건너뛴다")
    void skipsJobWithoutSubmissionOrMissingJob() {
        Job job = givenJob(JobStatus.MATCHED, null);
        when(jobSubmissionRepository.findFirstByJobIdOrderByRevisionNumberDesc(42L)).thenReturn(Optional.empty());

        assertThat(jobService.autoCompleteSubmission(42L, REFERENCE_TIME)).isEmpty();
        assertThat(jobService.autoCompleteSubmission(43L, REFERENCE_TIME)).isEmpty();

        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
    }

    private Job givenJob(JobStatus status, String demoSessionId) {
        Job job = Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .title("메뉴판 디자인")
                .status(status)
                .selectedStudentProfileId(7L)
                .revisionCount(2)
                .demoSessionId(demoSessionId)
                .build();
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job));
        return job;
    }

    private JobSubmission givenLatestSubmission(JobSubmissionType type, JobSubmissionReviewStatus reviewStatus,
            LocalDateTime createdAt) {
        JobSubmission submission = JobSubmission.builder()
                .id(81L)
                .jobId(42L)
                .submissionType(type)
                .revisionNumber(type == JobSubmissionType.DRAFT ? 0 : 1)
                .reviewStatus(reviewStatus)
                .createdAt(createdAt)
                .build();
        when(jobSubmissionRepository.findFirstByJobIdOrderByRevisionNumberDesc(42L))
                .thenReturn(Optional.of(submission));
        return submission;
    }
}
