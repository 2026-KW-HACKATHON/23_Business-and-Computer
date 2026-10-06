package com.gakkum.backend.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");

    @Test
    void rejectsInvalidStateTransitionsWithPaymentError() {
        Payment payment = Payment.pending(11L, 21L, "owner-123", "order-123", 100_000L, NOW);
        payment.supersede();

        assertPaymentError(() -> payment.supersede(), ErrorCode.PAYMENT_NOT_AVAILABLE);
        assertPaymentError(() -> payment.recordKakaoTid("T123"), ErrorCode.PAYMENT_NOT_AVAILABLE);
        assertPaymentError(() -> payment.approve(NOW), ErrorCode.PAYMENT_NOT_AVAILABLE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUPERSEDED);
    }

    @Test
    void rejectsApprovalWithoutTidOrApprovalTime() {
        Payment payment = Payment.pending(11L, 21L, "owner-123", "order-123", 100_000L, NOW);
        assertPaymentError(() -> payment.approve(NOW), ErrorCode.PAYMENT_NOT_AVAILABLE);

        payment.recordKakaoTid("T123");
        assertPaymentError(() -> payment.recordKakaoTid("T456"), ErrorCode.PAYMENT_NOT_AVAILABLE);
        assertPaymentError(() -> payment.approve(null), ErrorCode.PAYMENT_RESULT_MISMATCH);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void refundOnCancelKeepsTwentyPercentForStudentAndRefundsRest() {
        Payment payment = paidPayment(100_000L);

        payment.refundOnCancel(NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getStudentCompensationAmount()).isEqualTo(20_000L);
        assertThat(payment.getRefundAmount()).isEqualTo(80_000L);
        assertThat(payment.getRefundedAt()).isEqualTo(NOW);
    }

    @Test
    void refundOnCancelRoundsStudentCompensationDownAndRefundsRemainder() {
        Payment payment = paidPayment(10_001L);

        payment.refundOnCancel(NOW);

        assertThat(payment.getStudentCompensationAmount()).isEqualTo(2_000L);
        assertThat(payment.getRefundAmount()).isEqualTo(8_001L);
    }

    @Test
    void refundOnCancelRejectsUnpaidOrAlreadyRefundedPayment() {
        Payment pending = Payment.pending(11L, 21L, "owner-123", "order-123", 100_000L, NOW);
        assertPaymentError(() -> pending.refundOnCancel(NOW), ErrorCode.PAYMENT_NOT_AVAILABLE);

        Payment refunded = paidPayment(100_000L);
        refunded.refundOnCancel(NOW);
        assertPaymentError(() -> refunded.refundOnCancel(NOW), ErrorCode.PAYMENT_NOT_AVAILABLE);
        assertThat(refunded.getRefundAmount()).isEqualTo(80_000L);
    }

    private Payment paidPayment(Long amount) {
        Payment payment = Payment.pending(11L, 21L, "owner-123", "order-123", amount, NOW);
        payment.recordKakaoTid("T123");
        payment.approve(NOW);
        return payment;
    }

    private void assertPaymentError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    @Test
    @DisplayName("제안 결제의 대기 주문은 의뢰·지원서 없이 제안과 사장님이 입력한 수정 횟수·한마디를 보존한다")
    void createsPendingProposalPayment() {
        Payment payment = Payment.pendingForProposal(5L, "owner-123", "order-123", 50_000L, 2, "잘 부탁드립니다.", NOW);

        assertThat(payment.getProposalId()).isEqualTo(5L);
        assertThat(payment.getJobId()).isNull();
        assertThat(payment.getJobApplicationId()).isNull();
        assertThat(payment.getAmount()).isEqualTo(50_000L);
        assertThat(payment.getRevisionCount()).isEqualTo(2);
        assertThat(payment.getMessageToStudent()).isEqualTo("잘 부탁드립니다.");
        assertThat(payment.getRefundPolicyAgreedAt()).isEqualTo(NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("승인된 제안 결제에만 의뢰를 한 번 연결하고, 승인 전이거나 이미 연결됐거나 일반 결제이면 거부한다")
    void linksJobOnlyOnceToApprovedProposalPayment() {
        Payment payment = Payment.pendingForProposal(5L, "owner-123", "order-123", 50_000L, 0, null, NOW);
        payment.recordKakaoTid("T123");
        assertPaymentError(() -> payment.linkJob(42L), ErrorCode.PAYMENT_NOT_AVAILABLE);

        payment.approve(NOW);
        assertPaymentError(() -> payment.linkJob(null), ErrorCode.PAYMENT_NOT_AVAILABLE);
        payment.linkJob(42L);
        assertThat(payment.getJobId()).isEqualTo(42L);
        assertPaymentError(() -> payment.linkJob(43L), ErrorCode.PAYMENT_NOT_AVAILABLE);
        assertThat(payment.getJobId()).isEqualTo(42L);

        Payment general = Payment.pending(11L, 21L, "owner-123", "order-456", 100_000L, NOW);
        general.recordKakaoTid("T456");
        general.approve(NOW);
        assertPaymentError(() -> general.linkJob(42L), ErrorCode.PAYMENT_NOT_AVAILABLE);
        assertThat(general.getJobId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("의뢰서 거절 환불은 결제 금액 전액을 환불액으로, 학생 보상금을 0원으로 기록하고 환불 시각을 남긴다")
    void refundsFullAmountOnDecline() {
        Payment payment = Payment.pendingForProposal(5L, "owner-123", "order-123", 50_001L, 0, null, NOW);
        payment.recordKakaoTid("T123");
        payment.approve(NOW);
        payment.linkJob(42L);

        payment.refundOnDecline(NOW.plusSeconds(60));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getAmount()).isEqualTo(50_001L);
        assertThat(payment.getRefundAmount()).isEqualTo(50_001L);
        assertThat(payment.getStudentCompensationAmount()).isZero();
        assertThat(payment.getRefundedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(payment.getApprovedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("결제 완료가 아니거나 이미 환불된 주문은 의뢰서 거절 환불을 거부하고 저장된 환불 금액을 바꾸지 않는다")
    void refundOnDeclineRejectsUnpaidOrAlreadyRefundedPayment() {
        Payment pending = Payment.pendingForProposal(5L, "owner-123", "order-123", 50_000L, 0, null, NOW);
        assertPaymentError(() -> pending.refundOnDecline(NOW), ErrorCode.PAYMENT_NOT_AVAILABLE);
        assertThat(pending.getRefundAmount()).isNull();

        Payment refunded = paidPayment(100_000L);
        refunded.refundOnCancel(NOW);
        assertPaymentError(() -> refunded.refundOnDecline(NOW.plusSeconds(60)), ErrorCode.PAYMENT_NOT_AVAILABLE);
        assertThat(refunded.getRefundAmount()).isEqualTo(80_000L);
        assertThat(refunded.getStudentCompensationAmount()).isEqualTo(20_000L);
        assertThat(refunded.getRefundedAt()).isEqualTo(NOW);
    }
}
