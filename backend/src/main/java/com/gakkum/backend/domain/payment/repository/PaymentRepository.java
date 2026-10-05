package com.gakkum.backend.domain.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByJobIdAndStatus(Long jobId, PaymentStatus status);

    Optional<Payment> findByJobIdAndStatus(Long jobId, PaymentStatus status);

    boolean existsByProposalIdAndStatus(Long proposalId, PaymentStatus status);

    Optional<Payment> findByProposalIdAndStatus(Long proposalId, PaymentStatus status);

    /** 제안의 승인된 결제. 제안은 한 번만 결제되므로 PAID·REFUNDED 중 하나만 있다. */
    Optional<Payment> findByProposalIdAndStatusIn(Long proposalId, Collection<PaymentStatus> statuses);

    Optional<OrderTargetProjection> findProjectedByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findByOwnerUserIdAndStatusInOrderByApprovedAtDescIdDesc(
            String ownerUserId, Collection<PaymentStatus> statuses);

    List<Payment> findByJobIdInAndStatusInOrderByApprovedAtDescIdDesc(
            Collection<Long> jobIds, Collection<PaymentStatus> statuses);

    /** 주문이 가리키는 잠금 대상. 일반 결제는 의뢰, 제안 결제는 제안이다. */
    interface OrderTargetProjection {
        Long getJobId();

        Long getProposalId();
    }
}
