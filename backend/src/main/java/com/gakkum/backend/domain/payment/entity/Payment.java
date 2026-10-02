package com.gakkum.backend.domain.payment.entity;

import java.time.Instant;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "payments")
public class Payment {

    // 진행 중 의뢰 취소 시 결제 금액 중 학생에게 지급하는 비율(%)
    private static final long STUDENT_COMPENSATION_PERCENT = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "job_application_id", nullable = false)
    private Long jobApplicationId;

    @Column(name = "owner_user_id", nullable = false, length = 26)
    private String ownerUserId;

    @Column(name = "order_id", nullable = false, unique = true, length = 36)
    private String orderId;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "refund_policy_agreed_at", nullable = false)
    private Instant refundPolicyAgreedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "payment_key", columnDefinition = "TEXT")
    private String paymentKey;

    @Column(name = "kakao_tid", unique = true, length = 20)
    private String kakaoTid;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "refund_amount")
    private Long refundAmount;

    @Column(name = "student_compensation_amount")
    private Long studentCompensationAmount;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    public static Payment pending(
            Long jobId,
            Long jobApplicationId,
            String ownerUserId,
            String orderId,
            Long amount,
            Instant refundPolicyAgreedAt) {
        return Payment.builder()
                .jobId(jobId)
                .jobApplicationId(jobApplicationId)
                .ownerUserId(ownerUserId)
                .orderId(orderId)
                .amount(amount)
                .refundPolicyAgreedAt(refundPolicyAgreedAt)
                .status(PaymentStatus.PENDING)
                .build();
    }

    public void supersede() {
        if (status != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        status = PaymentStatus.SUPERSEDED;
    }

    public void recordKakaoTid(String tid) {
        if (status != PaymentStatus.PENDING || kakaoTid != null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        kakaoTid = tid;
    }

    public void failReady() {
        if (status == PaymentStatus.PENDING) {
            status = PaymentStatus.READY_FAILED;
        }
    }

    public void approve(Instant approvedAt) {
        if (status != PaymentStatus.PENDING || kakaoTid == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        if (approvedAt == null) {
            throw new BusinessException(ErrorCode.PAYMENT_RESULT_MISMATCH);
        }
        status = PaymentStatus.PAID;
        this.approvedAt = approvedAt;
    }

    /** 결제 완료(PAID) 주문을 의뢰 취소로 환불 처리한다. 학생 보상금은 원 단위 버림, 나머지를 환불 금액으로 둔다. */
    public void refundOnCancel(Instant refundedAt) {
        if (status != PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        studentCompensationAmount = amount * STUDENT_COMPENSATION_PERCENT / 100;
        refundAmount = amount - studentCompensationAmount;
        status = PaymentStatus.REFUNDED;
        this.refundedAt = refundedAt;
    }
}
