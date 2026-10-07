package com.gakkum.backend.domain.proposal.entity;

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

class ProposalTest {

    private static final LocalDateTime REJECTED_AT = LocalDateTime.of(2026, 10, 7, 3, 0);

    @Test
    @DisplayName("결제 전 제안은 결제 승인으로 수락 대기가 되고 학생의 작업 시작으로 수락된다")
    void movesFromPendingToAwaitingStartToAccepted() {
        Proposal proposal = proposal(ProposalStatus.PENDING);

        proposal.awaitStart();
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.AWAITING_START);

        proposal.accept();
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("결제 전이 아닌 제안은 다시 수락 대기로 넘기지 않고 409로 거부한다")
    void rejectsAwaitStartUnlessPending(ProposalStatus status) {
        Proposal proposal = proposal(status);

        assertThatThrownBy(proposal::awaitStart)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_PAYMENT_NOT_AVAILABLE));
        assertThat(proposal.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("이미 수락된 제안의 작업 시작 재요청은 상태를 그대로 둔다")
    void acceptIsIdempotent() {
        Proposal proposal = proposal(ProposalStatus.ACCEPTED);

        proposal.accept();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = { "PENDING", "REJECTED", "CANCELLED" })
    @DisplayName("결제되지 않았거나 거절·취소된 제안은 수락하지 않고 409로 거부한다")
    void rejectsAcceptWithoutPayment(ProposalStatus status) {
        Proposal proposal = proposal(status);

        assertThatThrownBy(proposal::accept)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOB_START_NOT_AVAILABLE));
        assertThat(proposal.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("결제 전 제안을 취소하면 취소 상태가 되고 공감 수가 0으로 돌아간다")
    void cancelsPendingProposalAndResetsLikeCount() {
        Proposal proposal = Proposal.builder().id(5L).status(ProposalStatus.PENDING).likeCount(3).build();

        proposal.cancel();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        assertThat(proposal.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("이미 취소된 제안의 취소 재요청은 상태와 공감 수를 그대로 둔다")
    void cancelIsIdempotent() {
        Proposal proposal = Proposal.builder().id(5L).status(ProposalStatus.CANCELLED).likeCount(0).build();

        proposal.cancel();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        assertThat(proposal.getLikeCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = { "AWAITING_START", "ACCEPTED", "REJECTED" })
    @DisplayName("결제됐거나 거절된 제안은 취소하지 않고 409로 거부하며 상태와 공감 수를 그대로 둔다")
    void rejectsCancelUnlessPending(ProposalStatus status) {
        Proposal proposal = Proposal.builder().id(5L).status(status).likeCount(3).build();

        assertThatThrownBy(proposal::cancel)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_CANCEL_NOT_AVAILABLE));
        assertThat(proposal.getStatus()).isEqualTo(status);
        assertThat(proposal.getLikeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("마감일은 기준일에 초안·최종 기간을 더하고 기간이 0이면 기준일 당일이다")
    void calculatesDeadlinesFromBaseDate() {
        LocalDate base = LocalDate.of(2026, 10, 30);

        Proposal proposal = Proposal.builder().draftDays(3).finalDays(7).build();
        assertThat(proposal.draftDeadlineFrom(base)).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(proposal.finalDeadlineFrom(base)).isEqualTo(LocalDate.of(2026, 11, 6));

        Proposal sameDay = Proposal.builder().draftDays(0).finalDays(0).build();
        assertThat(sameDay.draftDeadlineFrom(base)).isEqualTo(base);
        assertThat(sameDay.finalDeadlineFrom(base)).isEqualTo(base);
    }

    @Test
    @DisplayName("의뢰 설명은 고객 문제·해결 방안·작업 계획을 제목으로 구분해 합친다")
    void buildsJobDescription() {
        Proposal proposal = Proposal.builder()
                .customerProblem("메뉴를 알아보기 어렵습니다.")
                .proposedSolution("사진 메뉴판으로 바꿉니다.")
                .workPlan("촬영 후 편집합니다.")
                .build();

        assertThat(proposal.toJobDescription()).isEqualTo(
                "[고객 문제]\n메뉴를 알아보기 어렵습니다.\n\n[해결 방안]\n사진 메뉴판으로 바꿉니다.\n\n[작업 계획]\n촬영 후 편집합니다.");
    }

    @Test
    @DisplayName("공감 수는 1씩 오르고 내리며 0에서는 더 내려가지 않는다")
    void changesLikeCountByOneAndNeverBelowZero() {
        Proposal proposal = Proposal.builder().id(5L).likeCount(0).build();

        proposal.increaseLikeCount();
        proposal.increaseLikeCount();
        assertThat(proposal.getLikeCount()).isEqualTo(2);

        proposal.decreaseLikeCount();
        assertThat(proposal.getLikeCount()).isEqualTo(1);

        proposal.decreaseLikeCount();
        proposal.decreaseLikeCount();
        assertThat(proposal.getLikeCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(ProposalStatus.class)
    @DisplayName("공감 수 변경은 제안 상태와 무관하고 상태를 바꾸지 않는다")
    void changesLikeCountInEveryStatus(ProposalStatus status) {
        Proposal proposal = Proposal.builder().id(5L).status(status).likeCount(3).build();

        proposal.increaseLikeCount();
        assertThat(proposal.getLikeCount()).isEqualTo(4);
        proposal.decreaseLikeCount();
        assertThat(proposal.getLikeCount()).isEqualTo(3);
        assertThat(proposal.getStatus()).isEqualTo(status);
    }

    private Proposal proposal(ProposalStatus status) {
        return Proposal.builder().id(5L).status(status).build();
    }

    @Test
    @DisplayName("수락 대기 제안은 의뢰서 거절로 거절 상태가 되고 공감 수는 그대로다")
    void rejectsAwaitingStartProposal() {
        Proposal proposal = Proposal.builder().id(5L).status(ProposalStatus.AWAITING_START).likeCount(3).build();

        proposal.rejectByStudent(REJECTED_AT);

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(proposal.getRejectedBy()).isEqualTo(ProposalRejectedBy.STUDENT);
        assertThat(proposal.getRejectedAt()).isEqualTo(REJECTED_AT);
        assertThat(proposal.getLikeCount()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = "AWAITING_START", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("수락 대기가 아닌(결제 전·시작·이미 거절·취소된) 제안의 거절은 JOB_DECLINE_409로 거부하고 상태를 바꾸지 않는다")
    void rejectsRejectOfProposalNotAwaiting(ProposalStatus status) {
        Proposal proposal = proposal(status);

        assertThatThrownBy(() -> proposal.rejectByStudent(REJECTED_AT))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOB_DECLINE_NOT_AVAILABLE));
        assertThat(proposal.getStatus()).isEqualTo(status);
        assertThat(proposal.getRejectedBy()).isNull();
        assertThat(proposal.getRejectedAt()).isNull();
    }

    @Test
    @DisplayName("결제 전 제안은 사장님 거절로 거절 상태가 되고 거절 주체와 시각을 남기며 공감 수는 그대로다")
    void ownerRejectsPendingProposal() {
        Proposal proposal = Proposal.builder().id(5L).status(ProposalStatus.PENDING).likeCount(3).build();

        proposal.rejectByOwner(REJECTED_AT);

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(proposal.getRejectedBy()).isEqualTo(ProposalRejectedBy.OWNER);
        assertThat(proposal.getRejectedAt()).isEqualTo(REJECTED_AT);
        assertThat(proposal.getLikeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("사장님이 이미 거절한 제안의 거절 재요청은 최초 거절 시각을 그대로 둔다")
    void ownerRejectIsIdempotent() {
        Proposal proposal = Proposal.builder().id(5L).status(ProposalStatus.PENDING).likeCount(3).build();
        proposal.rejectByOwner(REJECTED_AT);

        proposal.rejectByOwner(REJECTED_AT.plusHours(1));

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(proposal.getRejectedBy()).isEqualTo(ProposalRejectedBy.OWNER);
        assertThat(proposal.getRejectedAt()).isEqualTo(REJECTED_AT);
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = { "AWAITING_START", "ACCEPTED", "CANCELLED" })
    @DisplayName("결제됐거나 취소된 제안의 사장님 거절은 PROPOSAL_409_REJECT로 거부하고 상태와 거절 기록을 바꾸지 않는다")
    void rejectsOwnerRejectUnlessPending(ProposalStatus status) {
        Proposal proposal = proposal(status);

        assertThatThrownBy(() -> proposal.rejectByOwner(REJECTED_AT))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_REJECT_NOT_AVAILABLE));
        assertThat(proposal.getStatus()).isEqualTo(status);
        assertThat(proposal.getRejectedBy()).isNull();
        assertThat(proposal.getRejectedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ProposalRejectedBy.class, names = "STUDENT")
    @DisplayName("학생이 거절한 제안의 사장님 거절은 PROPOSAL_409_REJECT로 거부하고 학생의 거절 기록을 그대로 둔다")
    void rejectsOwnerRejectOfStudentRejectedProposal(ProposalRejectedBy rejectedBy) {
        Proposal proposal = Proposal.builder().id(5L).status(ProposalStatus.REJECTED).rejectedBy(rejectedBy)
                .rejectedAt(REJECTED_AT).build();

        assertThatThrownBy(() -> proposal.rejectByOwner(REJECTED_AT.plusHours(1)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_REJECT_NOT_AVAILABLE));
        assertThat(proposal.getRejectedBy()).isEqualTo(rejectedBy);
        assertThat(proposal.getRejectedAt()).isEqualTo(REJECTED_AT);
    }

    @Test
    @DisplayName("거절 주체가 기록되지 않은 기존 거절 제안의 사장님 거절은 PROPOSAL_409_REJECT로 거부하고 기록을 채우지 않는다")
    void rejectsOwnerRejectOfLegacyRejectedProposal() {
        Proposal proposal = proposal(ProposalStatus.REJECTED);

        assertThatThrownBy(() -> proposal.rejectByOwner(REJECTED_AT))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_REJECT_NOT_AVAILABLE));
        assertThat(proposal.getRejectedBy()).isNull();
        assertThat(proposal.getRejectedAt()).isNull();
    }
}
