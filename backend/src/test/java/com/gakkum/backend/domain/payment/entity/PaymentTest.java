package com.gakkum.backend.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

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
}
