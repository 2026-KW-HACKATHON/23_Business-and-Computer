package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.gakkum.backend.domain.job.dto.JobCommandDto.RequestJobSubmissionRevisionCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobRepository.RevisionRequestTargetProjection;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository.ReviewTargetProjection;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobSubmissionRevisionRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T03:15:30Z");
    private static final String MESSAGE = "로고를 조금 더 크게 해주세요.";
    private static final List<String> IMAGES = List.of(
            "https://images.example.com/images/job/owner/b.png", "https://images.example.com/images/job/owner/a.png");

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, mock(JobSpecialtyRepository.class), mock(JobApplicationRepository.class),
            jobSubmissionRepository, Clock.fixed(NOW, ZoneId.of("UTC")));

    @Test
    @DisplayName("검토 대기 초안에 수정을 요청하면 잠근 의뢰 안에서 REVISION_REQUESTED로 바꾼다")
    void requestsRevisionOnPendingDraft() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);

        jobService.requestRevision(command(81L), 5L);

        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.REVISION_REQUESTED);
        verify(jobRepository).findByIdAndOwnerProfileId(42L, 5L);
    }

    @Test
    @DisplayName("수정을 요청하면 서버 로컬 시각 기준 요청 시각을 reviewedAt에 기록한다")
    void recordsRevisionRequestedAt() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);

        jobService.requestRevision(command(81L), 5L);

        assertThat(submission.getReviewedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneId.systemDefault()));
    }

    @Test
    @DisplayName("수정을 요청하면 요청 내용과 참고 사진을 순서대로 기록하고 학생의 제출 메시지와 파일은 그대로 둔다")
    void recordsRevisionRequestContent() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);

        jobService.requestRevision(command(81L), 5L);

        assertThat(submission.getReviewComment()).isEqualTo(MESSAGE);
        assertThat(submission.getRevisionReferenceImageUrls()).containsExactlyElementsOf(IMAGES);
        assertThat(submission.getMessage()).isEqualTo("초안입니다.");
        assertThat(submission.getFileUrls()).containsExactly("https://example.com/draft.pdf");
    }

    @Test
    @DisplayName("참고 사진 없이 수정을 요청하면 사진 목록을 빈 목록으로 기록한다")
    void recordsRevisionRequestWithoutImages() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);

        jobService.requestRevision(
                RequestJobSubmissionRevisionCommand.of("KAKAO_12345", 42L, 81L, MESSAGE, List.of()), 5L);

        assertThat(submission.getReviewComment()).isEqualTo(MESSAGE);
        assertThat(submission.getRevisionReferenceImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("거부된 수정 요청은 요청 내용·참고 사진·요청 시각을 남기지 않는다")
    void leavesSubmissionUntouchedWhenRejected() {
        givenOwnedJob(JobStatus.CLOSED, 2);
        JobSubmission submission = givenSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);

        assertError(() -> jobService.requestRevision(command(81L), 5L),
                ErrorCode.JOB_SUBMISSION_REVIEW_NOT_AVAILABLE);
        assertThat(submission.getReviewComment()).isNull();
        assertThat(submission.getRevisionReferenceImageUrls()).isEmpty();
        assertThat(submission.getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("사전 확인은 의뢰 행을 잠그지 않고 프로젝션으로 읽어 수정 요청 가능 여부만 확인한다")
    void validatesWithoutLock() {
        givenTargetJob(JobStatus.MATCHED, 2);
        givenTargetSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);

        jobService.validateRevisionRequestable(42L, 81L, 5L);

        verify(jobRepository, never()).findByIdAndOwnerProfileId(any(), any());
        verify(jobSubmissionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("사전 확인은 저장과 같은 조건·순서로 거부한다")
    void validatesWithSameRulesAsRequest() {
        when(jobRepository.findRevisionRequestTargetByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.empty());
        assertError(() -> jobService.validateRevisionRequestable(42L, 81L, 5L), ErrorCode.JOB_NOT_FOUND);

        givenTargetJob(JobStatus.MATCHED, 2);
        when(jobSubmissionRepository.findReviewTargetById(99L)).thenReturn(Optional.empty());
        assertError(() -> jobService.validateRevisionRequestable(42L, 99L, 5L), ErrorCode.JOB_SUBMISSION_NOT_FOUND);

        givenTargetSubmission(91L, 43L, 0, JobSubmissionReviewStatus.PENDING);
        assertError(() -> jobService.validateRevisionRequestable(42L, 91L, 5L), ErrorCode.JOB_SUBMISSION_NOT_FOUND);

        givenTargetSubmission(82L, 42L, 0, JobSubmissionReviewStatus.REVISION_REQUESTED);
        assertError(() -> jobService.validateRevisionRequestable(42L, 82L, 5L),
                ErrorCode.JOB_SUBMISSION_ALREADY_REVIEWED);

        givenTargetSubmission(88L, 42L, 2, JobSubmissionReviewStatus.PENDING);
        assertError(() -> jobService.validateRevisionRequestable(42L, 88L, 5L),
                ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);

        givenTargetJob(JobStatus.CANCELLED, 2);
        givenTargetSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);
        assertError(() -> jobService.validateRevisionRequestable(42L, 81L, 5L),
                ErrorCode.JOB_SUBMISSION_REVIEW_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("마지막 허용 수정안 직전 번호의 수정안에는 수정을 요청할 수 있다")
    void allowsRequestBelowLimit() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(87L, 42L, 1, JobSubmissionReviewStatus.PENDING);

        jobService.requestRevision(command(87L), 5L);

        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.REVISION_REQUESTED);
    }

    @Test
    @DisplayName("마지막 허용 수정안까지 제출됐으면 JOB_SUBMISSION_409_REVISION_LIMIT으로 거부한다")
    void rejectsWhenRevisionLimitReached() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(88L, 42L, 2, JobSubmissionReviewStatus.PENDING);

        assertError(() -> jobService.requestRevision(command(88L), 5L), ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);
        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
        assertThat(submission.getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("수정 가능 횟수가 0이면 초안에도 수정 요청을 거부한다")
    void rejectsWhenNoRevisionAllowed() {
        givenOwnedJob(JobStatus.MATCHED, 0);
        givenSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);

        assertError(() -> jobService.requestRevision(command(81L), 5L), ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);
    }

    @ParameterizedTest
    @EnumSource(value = JobSubmissionReviewStatus.class, names = { "REVISION_REQUESTED", "APPROVED" })
    @DisplayName("이미 처리된 제출물(중복 요청·오래된 제출물 ID)은 JOB_SUBMISSION_409_REVIEWED로 거부한다")
    void rejectsAlreadyReviewedSubmission(JobSubmissionReviewStatus status) {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(81L, 42L, 0, status);

        assertError(() -> jobService.requestRevision(command(81L), 5L), ErrorCode.JOB_SUBMISSION_ALREADY_REVIEWED);
        assertThat(submission.getReviewStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("다른 사업주의 의뢰이면 JOB_404로 거부하고 제출물을 조회하지 않는다")
    void rejectsOtherOwnersJob() {
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.empty());

        assertError(() -> jobService.requestRevision(command(81L), 5L), ErrorCode.JOB_NOT_FOUND);
        verify(jobSubmissionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("존재하지 않는 제출물이면 JOB_SUBMISSION_404로 거부한다")
    void rejectsMissingSubmission() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        when(jobSubmissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertError(() -> jobService.requestRevision(command(99L), 5L), ErrorCode.JOB_SUBMISSION_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 의뢰의 제출물이면 JOB_SUBMISSION_404로 거부한다")
    void rejectsSubmissionOfOtherJob() {
        givenOwnedJob(JobStatus.MATCHED, 2);
        JobSubmission submission = givenSubmission(91L, 43L, 0, JobSubmissionReviewStatus.PENDING);

        assertError(() -> jobService.requestRevision(command(91L), 5L), ErrorCode.JOB_SUBMISSION_NOT_FOUND);
        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
    }

    @Test
    @DisplayName("진행 중(MATCHED)이 아닌 의뢰이면 JOB_SUBMISSION_409_REVIEW_STATUS로 거부한다")
    void rejectsJobNotMatched() {
        givenOwnedJob(JobStatus.CLOSED, 2);
        JobSubmission submission = givenSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);

        assertError(() -> jobService.requestRevision(command(81L), 5L), ErrorCode.JOB_SUBMISSION_REVIEW_NOT_AVAILABLE);
        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
    }

    private void givenOwnedJob(JobStatus status, int revisionCount) {
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .status(status)
                .selectedStudentProfileId(7L)
                .revisionCount(revisionCount)
                .build()));
    }

    private JobSubmission givenSubmission(
            Long id, Long jobId, int revisionNumber, JobSubmissionReviewStatus reviewStatus) {
        JobSubmission submission = JobSubmission.builder()
                .id(id)
                .jobId(jobId)
                .submissionType(revisionNumber == 0 ? JobSubmissionType.DRAFT : JobSubmissionType.REVISION)
                .revisionNumber(revisionNumber)
                .fileUrls(List.of("https://example.com/draft.pdf"))
                .message("초안입니다.")
                .reviewStatus(reviewStatus)
                .build();
        when(jobSubmissionRepository.findById(id)).thenReturn(Optional.of(submission));
        return submission;
    }

    private void givenTargetJob(JobStatus status, int revisionCount) {
        RevisionRequestTargetProjection job = mock(RevisionRequestTargetProjection.class);
        when(job.getStatus()).thenReturn(status);
        when(job.getRevisionCount()).thenReturn(revisionCount);
        when(jobRepository.findRevisionRequestTargetByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(job));
    }

    private void givenTargetSubmission(
            Long id, Long jobId, int revisionNumber, JobSubmissionReviewStatus reviewStatus) {
        ReviewTargetProjection submission = mock(ReviewTargetProjection.class);
        when(submission.getJobId()).thenReturn(jobId);
        when(submission.getReviewStatus()).thenReturn(reviewStatus);
        when(submission.getRevisionNumber()).thenReturn(revisionNumber);
        when(jobSubmissionRepository.findReviewTargetById(id)).thenReturn(Optional.of(submission));
    }

    private RequestJobSubmissionRevisionCommand command(Long submissionId) {
        return RequestJobSubmissionRevisionCommand.of("KAKAO_12345", 42L, submissionId, MESSAGE, IMAGES);
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
