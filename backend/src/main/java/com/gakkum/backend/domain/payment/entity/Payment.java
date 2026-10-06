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

    // 제안 결제는 승인 시 만든 의뢰를 연결하기 전까지 null
    @Column(name = "job_id")
    private Long jobId;

    // 제안 결제는 지원서를 쓰지 않아 null
    @Column(name = "job_application_id")
    private Long jobApplicationId;

    // 제안 결제가 가리키는 제안. 일반 결제는 null
    @Column(name = "proposal_id")
    private Long proposalId;

    // 제안 결제 준비 시 사장님이 입력한 수정 횟수. 승인 시 의뢰로 옮긴다
    @Column(name = "revision_count")
    private Integer revisionCount;

    // 제안 결제 준비 시 사장님이 학생에게 남긴 한마디. 입력하지 않으면 null
    @Column(name = "message_to_student", columnDefinition = "TEXT")
    private String messageToStudent;

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

    /** 제안 결제의 대기 주문. 의뢰는 승인 시 만들어 연결하고 지원서는 쓰지 않는다. */
    public static Payment pendingForProposal(
            Long proposalId,
            String ownerUserId,
            String orderId,
            Long amount,
            Integer revisionCount,
            String messageToStudent,
            Instant refundPolicyAgreedAt) {
        return Payment.builder()
                .proposalId(proposalId)
                .ownerUserId(ownerUserId)
                .orderId(orderId)
                .amount(amount)
                .revisionCount(revisionCount)
                .messageToStudent(messageToStudent)
                .refundPolicyAgreedAt(refundPolicyAgreedAt)
                .status(PaymentStatus.PENDING)
                .build();
    }

    /** 승인된 제안 결제에 승인과 함께 만든 의뢰를 연결한다. 한 번만 연결할 수 있다. */
    public void linkJob(Long jobId) {
        if (proposalId == null || status != PaymentStatus.PAID || this.jobId != null || jobId == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        this.jobId = jobId;
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

    /** 결제 완료(PAID) 주문을 학생의 의뢰서 거절로 전액 환불 처리한다. 학생 보상금은 0원이다. */
    public void refundOnDecline(Instant refundedAt) {
        if (status != PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        studentCompensationAmount = 0L;
        refundAmount = amount;
        status = PaymentStatus.REFUNDED;
        this.refundedAt = refundedAt;
    }
}
