package com.gakkum.backend.domain.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByJobIdAndStatus(Long jobId, PaymentStatus status);

    Optional<Payment> findByJobIdAndStatus(Long jobId, PaymentStatus status);

    /** 의뢰서 거절의 환불이 한 번만 기록되도록 의뢰의 결제 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findLockedByJobIdAndStatus(Long jobId, PaymentStatus status);

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

    /*
     * 학생이 담당한 의뢰의 결제를 의뢰 완료 순으로 읽으려면 연관관계가 없는 Job을 조인해야 해서 메서드 이름으로 표현할 수 없다.
     * 의뢰 완료 시각 내림차순, 같은 시각은 의뢰 ID 내림차순으로 limit개까지 읽는다.
     * 완료 시각이 없는 의뢰는 조회하는 쪽이 데이터 오류로 거부하도록 맨 앞에 둔다.
     */
    @Query("""
            select p from Payment p join Job j on j.id = p.jobId
            where j.selectedStudentProfileId = :studentProfileId
              and j.status = :jobStatus
              and p.status = :paymentStatus
            order by j.completedAt desc nulls first, j.id desc
            """)
    List<Payment> findLatestCompletedByStudentProfileId(@Param("studentProfileId") Long studentProfileId,
            @Param("jobStatus") JobStatus jobStatus, @Param("paymentStatus") PaymentStatus paymentStatus,
            Limit limit);

    /** 주문이 가리키는 잠금 대상. 일반 결제는 의뢰, 제안 결제는 제안이다. */
    interface OrderTargetProjection {
        Long getJobId();

        Long getProposalId();
    }
}
