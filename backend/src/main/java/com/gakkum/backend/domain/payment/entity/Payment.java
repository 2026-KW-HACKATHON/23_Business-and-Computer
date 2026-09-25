package com.gakkum.backend.domain.payment.entity;

import java.time.Instant;

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

    @Column(name = "approved_at")
    private Instant approvedAt;

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
            throw new IllegalStateException("Only pending payments can be superseded");
        }
        status = PaymentStatus.SUPERSEDED;
    }
}
