package com.gakkum.backend.domain.job.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobTest {

    private static final LocalDate DRAFT_DEADLINE = LocalDate.of(2026, 10, 8);
    private static final LocalDate FINAL_DEADLINE = LocalDate.of(2026, 10, 12);
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 10, 20, 9, 30);

    @Test
    @DisplayName("제안으로 만든 의뢰는 담당 학생·제안·한마디가 채워진 수락 대기 상태이고 시작 시각과 취소용 남길 말은 비어 있다")
    void createsAwaitingStartJob() {
        Job job = awaitingStart();

        assertThat(job.getStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(job.getOwnerProfileId()).isEqualTo(7L);
        assertThat(job.getSelectedStudentProfileId()).isEqualTo(31L);
        assertThat(job.getProposalId()).isEqualTo(5L);
        assertThat(job.getBudget()).isEqualTo(50_000L);
        assertThat(job.getRevisionCount()).isEqualTo(2);
        assertThat(job.getAcceptanceMessage()).isEqualTo("잘 부탁드립니다.");
        assertThat(job.getMessageToStudent()).isNull();
        assertThat(job.getStartedAt()).isNull();
    }

    @Test
    @DisplayName("작업을 시작하면 진행 중이 되고 시작 시각을 기록하며, 마감일이 지난 늦은 시작에도 확정한 마감일을 바꾸지 않는다")
    void startsWithoutChangingDeadlines() {
        Job job = awaitingStart();

        job.start(STARTED_AT);

        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(job.getStartedAt()).isEqualTo(STARTED_AT);
        assertThat(STARTED_AT.toLocalDate()).isAfter(FINAL_DEADLINE);
        assertThat(job.getDraftDeadline()).isEqualTo(DRAFT_DEADLINE);
        assertThat(job.getFinalDeadline()).isEqualTo(FINAL_DEADLINE);
    }

    @Test
    @DisplayName("이미 시작한 제안 의뢰의 재요청은 기존 시작 시각을 덮어쓰지 않는다")
    void startIsIdempotent() {
        Job job = awaitingStart();
        job.start(STARTED_AT);

        job.start(STARTED_AT.plusDays(1));

        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(job.getStartedAt()).isEqualTo(STARTED_AT);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = "AWAITING_START", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("수락 대기가 아닌 일반 의뢰는 작업 시작을 409로 거부한다")
    void rejectsStartOfJobNotAwaiting(JobStatus status) {
        Job job = Job.builder().id(42L).status(status).build();

        assertThatThrownBy(() -> job.start(STARTED_AT))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOB_START_NOT_AVAILABLE));
        assertThat(job.getStatus()).isEqualTo(status);
        assertThat(job.getStartedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = { "CLOSED", "CANCELLED" })
    @DisplayName("완료되거나 취소된 제안 의뢰는 다시 시작하지 않는다")
    void rejectsStartOfFinishedProposalJob(JobStatus status) {
        Job job = Job.builder().id(42L).proposalId(5L).startedAt(STARTED_AT).status(status).build();

        assertThatThrownBy(() -> job.start(STARTED_AT.plusDays(1)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOB_START_NOT_AVAILABLE));
        assertThat(job.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("수락 대기 의뢰는 취소·완료·일반 결제 매칭을 허용하지 않는다")
    void awaitingStartRejectsOtherTransitions() {
        Job job = awaitingStart();

        assertCode(() -> job.cancel(STARTED_AT, "이유", "남길 말"), ErrorCode.JOB_CANCEL_NOT_AVAILABLE);
        assertCode(() -> job.complete(STARTED_AT), ErrorCode.JOB_SUBMISSION_REVIEW_NOT_AVAILABLE);
        assertCode(() -> job.match(31L), ErrorCode.PAYMENT_NOT_AVAILABLE);
        assertThat(job.getStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(job.getCompletedAt()).isNull();
    }

    private Job awaitingStart() {
        return Job.createAwaitingStart(7L, 31L, 5L, "메뉴판 개선 제안", "설명", 50_000L,
                DRAFT_DEADLINE, FINAL_DEADLINE, 2, "잘 부탁드립니다.", null);
    }

    private void assertCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    @Test
    @DisplayName("수락 대기 제안 의뢰를 거절하면 취소로 넘기고 거절 시각과 고정 취소 이유를 기록하며 작업 조건과 담당 학생은 보존한다")
    void declinesAwaitingStartJob() {
        Job job = awaitingStart();

        job.decline(STARTED_AT);

        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.getCompletedAt()).isEqualTo(STARTED_AT);
        assertThat(job.getCancelReason()).isEqualTo("학생이 작업 시작 전에 의뢰서를 거절했습니다.");
        assertThat(job.getMessageToStudent()).isNull();
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getSelectedStudentProfileId()).isEqualTo(31L);
        assertThat(job.getProposalId()).isEqualTo(5L);
        assertThat(job.getBudget()).isEqualTo(50_000L);
        assertThat(job.getDraftDeadline()).isEqualTo(DRAFT_DEADLINE);
        assertThat(job.getFinalDeadline()).isEqualTo(FINAL_DEADLINE);
        assertThat(job.getRevisionCount()).isEqualTo(2);
        assertThat(job.getAcceptanceMessage()).isEqualTo("잘 부탁드립니다.");
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = "AWAITING_START", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("수락 대기가 아닌(모집 중·시작·완료·이미 거절된) 의뢰의 거절은 JOB_DECLINE_409로 거부하고 아무것도 바꾸지 않는다")
    void rejectsDeclineOfJobNotAwaiting(JobStatus status) {
        Job job = Job.builder().id(42L).proposalId(5L).status(status).build();

        assertCode(() -> job.decline(STARTED_AT), ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
        assertThat(job.getStatus()).isEqualTo(status);
        assertThat(job.getCompletedAt()).isNull();
        assertThat(job.getCancelReason()).isNull();
    }

    @Test
    @DisplayName("시작한 적 없이 취소된 제안 의뢰만 학생의 거절로 보고, 일반 의뢰의 취소와 시작 후 취소는 사장님의 취소로 본다")
    void distinguishesStudentDeclineFromOwnerCancel() {
        Job declined = awaitingStart();
        declined.decline(STARTED_AT);
        assertThat(declined.isDeclinedByStudent()).isTrue();

        Job cancelledAfterStart = awaitingStart();
        cancelledAfterStart.start(STARTED_AT);
        cancelledAfterStart.cancel(STARTED_AT.plusDays(1), "이유", "남길 말");
        assertThat(cancelledAfterStart.isDeclinedByStudent()).isFalse();

        // 일반 의뢰는 결제 전·후 취소 모두 사장님의 취소다
        assertThat(Job.builder().id(42L).status(JobStatus.CANCELLED).build().isDeclinedByStudent()).isFalse();
        assertThat(Job.builder().id(42L).status(JobStatus.CANCELLED).selectedStudentProfileId(31L).build()
                .isDeclinedByStudent()).isFalse();
        assertThat(awaitingStart().isDeclinedByStudent()).isFalse();
    }
}
