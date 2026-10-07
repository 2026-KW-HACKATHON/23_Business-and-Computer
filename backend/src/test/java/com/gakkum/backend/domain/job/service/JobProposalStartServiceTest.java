package com.gakkum.backend.domain.job.service;

import com.gakkum.backend.domain.job.repository.JobRepository.DeclineTargetProjection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CancelJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobApplicationCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateProposalJobCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobRepository.StartTargetProjection;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobProposalStartServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:30:00Z");
    private static final LocalDateTime EXPECTED_STARTED_AT = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final LocalDate DRAFT_DEADLINE = LocalDate.of(2026, 10, 8);
    private static final LocalDate FINAL_DEADLINE = LocalDate.of(2026, 10, 12);

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(jobRepository, jobSpecialtyRepository,
            jobApplicationRepository, jobSubmissionRepository, Clock.fixed(NOW, ZoneId.of("UTC")));

    @Test
    @DisplayName("제안으로 수락 대기 의뢰를 저장하되 작업비는 희망 금액이 아닌 결제 금액을 쓰고, 제안의 소분류를 저장된 의뢰 ID로 복사하며 지원서는 만들지 않는다")
    @SuppressWarnings("unchecked")
    void createsAwaitingStartJobWithSpecialties() {
        when(jobRepository.saveAndFlush(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            return Job.builder().id(42L).status(job.getStatus()).proposalId(job.getProposalId())
                    .selectedStudentProfileId(job.getSelectedStudentProfileId()).build();
        });

        Proposal proposal = Proposal.builder().id(5L).ownerProfileId(7L).studentProfileId(31L)
                .title("메뉴판 개선 제안").customerProblem("문제").proposedSolution("해결").workPlan("계획")
                .proposedFee(50_000L).draftDays(3).finalDays(7).build();

        Job saved = jobService.createAwaitingStartJob(CreateProposalJobCommand.of(
                proposal, List.of(3L, 11L), 120_000L, LocalDate.of(2026, 10, 5), 2, "잘 부탁드립니다."));

        assertThat(saved.getId()).isEqualTo(42L);
        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).saveAndFlush(jobCaptor.capture());
        Job created = jobCaptor.getValue();
        assertThat(created.getStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(created.getOwnerProfileId()).isEqualTo(7L);
        assertThat(created.getSelectedStudentProfileId()).isEqualTo(31L);
        assertThat(created.getProposalId()).isEqualTo(5L);
        assertThat(created.getTitle()).isEqualTo("메뉴판 개선 제안");
        assertThat(created.getDescription()).isEqualTo("[고객 문제]\n문제\n\n[해결 방안]\n해결\n\n[작업 계획]\n계획");
        assertThat(created.getBudget()).isEqualTo(120_000L);
        assertThat(created.getDraftDeadline()).isEqualTo(DRAFT_DEADLINE);
        assertThat(created.getFinalDeadline()).isEqualTo(FINAL_DEADLINE);
        assertThat(created.getRevisionCount()).isEqualTo(2);
        assertThat(created.getAcceptanceMessage()).isEqualTo("잘 부탁드립니다.");

        ArgumentCaptor<List<JobSpecialty>> specialtyCaptor = ArgumentCaptor.forClass(List.class);
        verify(jobSpecialtyRepository).saveAll(specialtyCaptor.capture());
        assertThat(specialtyCaptor.getValue()).extracting(JobSpecialty::getJobId).containsOnly(42L);
        assertThat(specialtyCaptor.getValue()).extracting(JobSpecialty::getSpecialtyId).containsExactly(3L, 11L);
        verifyNoInteractions(jobApplicationRepository);
    }

    @Test
    @DisplayName("작업 시작 전 의뢰의 제안 ID를 잠금 없이 읽고, 없는 의뢰는 404, 일반 의뢰는 409, 담당 학생이 아니면 403으로 거부한다")
    void readsStartableProposalId() {
        StartTargetProjection proposalJob = startTarget(5L, 31L);
        StartTargetProjection generalJob = startTarget(null, 31L);
        when(jobRepository.findProjectedById(42L)).thenReturn(Optional.of(proposalJob));
        when(jobRepository.findProjectedById(43L)).thenReturn(Optional.of(generalJob));
        when(jobRepository.findProjectedById(99L)).thenReturn(Optional.empty());

        assertThat(jobService.getStartableProposalId(42L, 31L)).isEqualTo(5L);
        assertCode(() -> jobService.getStartableProposalId(99L, 31L), ErrorCode.JOB_NOT_FOUND);
        assertCode(() -> jobService.getStartableProposalId(43L, 31L), ErrorCode.JOB_START_NOT_AVAILABLE);
        assertCode(() -> jobService.getStartableProposalId(42L, 32L), ErrorCode.JOB_START_FORBIDDEN);
        // 엔티티로 읽으면 잠금 후 조회가 갱신되지 않으므로 의뢰 엔티티를 조회하지 않는다
        verify(jobRepository, never()).findById(any());
        verify(jobRepository, never()).findLockedById(any());
    }

    @Test
    @DisplayName("담당 학생이 수락 대기 의뢰를 시작하면 의뢰 행을 잠가 진행 중으로 넘기고 시작 시각을 기록하며 마감일은 그대로 둔다")
    void startsAwaitingJob() {
        Job job = proposalJob(JobStatus.AWAITING_START);
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job));

        Job started = jobService.startJob(42L, 5L, 31L);

        assertThat(started).isSameAs(job);
        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(job.getStartedAt()).isEqualTo(EXPECTED_STARTED_AT);
        assertThat(job.getDraftDeadline()).isEqualTo(DRAFT_DEADLINE);
        assertThat(job.getFinalDeadline()).isEqualTo(FINAL_DEADLINE);
    }

    @Test
    @DisplayName("이미 시작한 의뢰의 재요청은 기존 시작 시각을 그대로 반환한다")
    void restartReturnsExistingStartedAt() {
        LocalDateTime firstStartedAt = LocalDateTime.of(2026, 10, 5, 9, 0);
        Job job = Job.builder().id(42L).proposalId(5L).selectedStudentProfileId(31L)
                .status(JobStatus.MATCHED).startedAt(firstStartedAt).build();
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job));

        Job started = jobService.startJob(42L, 5L, 31L);

        assertThat(started.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(started.getStartedAt()).isEqualTo(firstStartedAt);
    }

    @Test
    @DisplayName("잠근 의뢰의 제안이 먼저 읽은 제안과 다르면 409, 담당 학생이 아니면 403, 완료·취소된 의뢰면 409로 거부한다")
    void revalidatesUnderLock() {
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(proposalJob(JobStatus.AWAITING_START)));
        when(jobRepository.findLockedById(44L)).thenReturn(Optional.of(
                Job.builder().id(44L).proposalId(5L).selectedStudentProfileId(31L)
                        .status(JobStatus.CANCELLED).startedAt(LocalDateTime.of(2026, 10, 5, 9, 0)).build()));
        when(jobRepository.findLockedById(99L)).thenReturn(Optional.empty());

        assertCode(() -> jobService.startJob(42L, 6L, 31L), ErrorCode.JOB_START_NOT_AVAILABLE);
        assertCode(() -> jobService.startJob(42L, 5L, 32L), ErrorCode.JOB_START_FORBIDDEN);
        assertCode(() -> jobService.startJob(44L, 5L, 31L), ErrorCode.JOB_START_NOT_AVAILABLE);
        assertCode(() -> jobService.startJob(99L, 5L, 31L), ErrorCode.JOB_NOT_FOUND);
    }

    @Test
    @DisplayName("제안 ID 목록에서 연결 의뢰와 취소 상태를 한 번에 조회하고 빈 목록은 조회하지 않는다")
    void findsJobsByProposalIdsInOneQuery() {
        Job cancelled = Job.builder().id(42L).proposalId(5L).status(JobStatus.CANCELLED).build();
        Job started = Job.builder().id(43L).proposalId(7L).status(JobStatus.MATCHED).build();
        when(jobRepository.findByProposalIdIn(List.of(5L, 6L, 7L)))
                .thenReturn(List.of(cancelled, started));

        Map<Long, Job> jobs = jobService.getJobsByProposalIds(List.of(5L, 6L, 7L));

        assertThat(jobs).containsOnly(Map.entry(5L, cancelled), Map.entry(7L, started));
        assertThat(jobs.get(5L).getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(jobService.getJobsByProposalIds(List.of())).isEmpty();
        verify(jobRepository).findByProposalIdIn(anyCollection());
    }

    @Test
    @DisplayName("수락 대기 의뢰의 상세는 진행 단계를 AWAITING_START로 계산하고 제출물을 조회하지 않는다")
    void calculatesAwaitingStartStage() {
        when(jobRepository.findById(42L)).thenReturn(Optional.of(proposalJob(JobStatus.AWAITING_START)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of());

        assertThat(jobService.getJobDetail(42L).getProgressStage()).isEqualTo(JobProgressStage.AWAITING_START);
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("수락 대기 의뢰에는 지원·작업물 업로드와 제출·취소를 허용하지 않는다")
    void rejectsOtherOperationsWhileAwaitingStart() {
        Job job = proposalJob(JobStatus.AWAITING_START);
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job));
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job));
        when(jobRepository.findByIdAndOwnerProfileId(42L, 7L)).thenReturn(Optional.of(job));

        assertCode(() -> jobService.createJobApplication(
                CreateJobApplicationCommand.of("KAKAO_1", 42L, "요약", "계획", "전달"), 33L, null),
                ErrorCode.JOB_APPLICATION_NOT_AVAILABLE);
        assertCode(() -> jobService.getSubmittableJob(42L, 31L), ErrorCode.JOB_SUBMISSION_NOT_AVAILABLE);
        assertCode(() -> jobService.validateDraftSubmittable(42L, 31L), ErrorCode.JOB_SUBMISSION_NOT_AVAILABLE);
        assertCode(() -> jobService.cancelJob(CancelJobCommand.of("KAKAO_2", 42L, "이유", "남길 말"), 7L),
                ErrorCode.JOB_CANCEL_NOT_AVAILABLE);
        assertCode(() -> jobService.getPayableJobForUpdate(42L, 7L), ErrorCode.PAYMENT_NOT_AVAILABLE);
        assertThat(job.getStatus()).isEqualTo(JobStatus.AWAITING_START);
    }

    private Job proposalJob(JobStatus status) {
        return Job.builder().id(42L).ownerProfileId(7L).proposalId(5L).selectedStudentProfileId(31L)
                .status(status).draftDeadline(DRAFT_DEADLINE).finalDeadline(FINAL_DEADLINE).build();
    }

    private StartTargetProjection startTarget(Long proposalId, Long selectedStudentProfileId) {
        StartTargetProjection target = mock(StartTargetProjection.class);
        when(target.getProposalId()).thenReturn(proposalId);
        when(target.getSelectedStudentProfileId()).thenReturn(selectedStudentProfileId);
        return target;
    }

    private void assertCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    @Test
    @DisplayName("거절 전 의뢰의 제안 ID를 잠금 없이 읽고, 없거나 격리 범위가 다른 의뢰는 404, 일반 의뢰는 409, 담당 학생이 아니면 403으로 거부한다")
    void readsDeclinableProposalId() {
        DeclineTargetProjection proposalJob = declineTarget(5L, 31L, null);
        DeclineTargetProjection generalJob = declineTarget(null, 31L, null);
        DeclineTargetProjection demoJob = declineTarget(6L, 31L, "01K6DEMO00000000000000000A");
        when(jobRepository.findDeclineTargetById(42L)).thenReturn(Optional.of(proposalJob));
        when(jobRepository.findDeclineTargetById(43L)).thenReturn(Optional.of(generalJob));
        when(jobRepository.findDeclineTargetById(44L)).thenReturn(Optional.of(demoJob));
        when(jobRepository.findDeclineTargetById(99L)).thenReturn(Optional.empty());

        assertThat(jobService.getDeclinableProposalId(42L, 31L, null)).isEqualTo(5L);
        assertThat(jobService.getDeclinableProposalId(44L, 31L, "01K6DEMO00000000000000000A")).isEqualTo(6L);
        assertCode(() -> jobService.getDeclinableProposalId(99L, 31L, null), ErrorCode.JOB_NOT_FOUND);
        // 실제 학생과 데모 의뢰, 데모 학생과 실제·다른 세션 의뢰는 서로 보이지 않는다
        assertCode(() -> jobService.getDeclinableProposalId(44L, 31L, null), ErrorCode.JOB_NOT_FOUND);
        assertCode(() -> jobService.getDeclinableProposalId(42L, 31L, "01K6DEMO00000000000000000A"),
                ErrorCode.JOB_NOT_FOUND);
        assertCode(() -> jobService.getDeclinableProposalId(44L, 31L, "01K6DEMO00000000000000000B"),
                ErrorCode.JOB_NOT_FOUND);
        assertCode(() -> jobService.getDeclinableProposalId(43L, 31L, null), ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
        assertCode(() -> jobService.getDeclinableProposalId(42L, 32L, null), ErrorCode.JOB_DECLINE_FORBIDDEN);
        verify(jobRepository, never()).findById(any());
        verify(jobRepository, never()).findLockedById(any());
    }

    @Test
    @DisplayName("담당 학생이 수락 대기 의뢰를 거절하면 의뢰 행을 잠가 취소로 넘기고 거절 시각과 고정 취소 이유를 기록하며 마감일과 담당 학생은 그대로 둔다")
    void declinesAwaitingJob() {
        Job job = proposalJob(JobStatus.AWAITING_START);
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job));

        Job declined = jobService.declineJob(42L, 5L, 31L);

        assertThat(declined).isSameAs(job);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.getCompletedAt()).isEqualTo(EXPECTED_STARTED_AT);
        assertThat(job.getCancelReason()).isEqualTo(Job.DECLINE_CANCEL_REASON);
        assertThat(job.getMessageToStudent()).isNull();
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getSelectedStudentProfileId()).isEqualTo(31L);
        assertThat(job.getDraftDeadline()).isEqualTo(DRAFT_DEADLINE);
        assertThat(job.getFinalDeadline()).isEqualTo(FINAL_DEADLINE);
    }

    @Test
    @DisplayName("거절은 잠근 의뢰의 제안이 먼저 읽은 제안과 다르면 409, 담당 학생이 아니면 403, 이미 시작·종료·거절된 의뢰면 409로 거부한다")
    void revalidatesDeclineUnderLock() {
        Job awaiting = proposalJob(JobStatus.AWAITING_START);
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(awaiting));
        when(jobRepository.findLockedById(99L)).thenReturn(Optional.empty());

        assertCode(() -> jobService.declineJob(42L, 6L, 31L), ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
        assertCode(() -> jobService.declineJob(42L, 5L, 32L), ErrorCode.JOB_DECLINE_FORBIDDEN);
        assertCode(() -> jobService.declineJob(99L, 5L, 31L), ErrorCode.JOB_NOT_FOUND);
        assertThat(awaiting.getStatus()).isEqualTo(JobStatus.AWAITING_START);

        for (JobStatus status : List.of(JobStatus.MATCHED, JobStatus.CLOSED, JobStatus.CANCELLED)) {
            Job job = proposalJob(status);
            when(jobRepository.findLockedById(44L)).thenReturn(Optional.of(job));
            assertCode(() -> jobService.declineJob(44L, 5L, 31L), ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
            assertThat(job.getStatus()).isEqualTo(status);
            assertThat(job.getCompletedAt()).isNull();
        }
    }

    private DeclineTargetProjection declineTarget(Long proposalId, Long selectedStudentProfileId,
            String demoSessionId) {
        DeclineTargetProjection target = mock(DeclineTargetProjection.class);
        when(target.getProposalId()).thenReturn(proposalId);
        when(target.getSelectedStudentProfileId()).thenReturn(selectedStudentProfileId);
        when(target.getDemoSessionId()).thenReturn(demoSessionId);
        return target;
    }
}
