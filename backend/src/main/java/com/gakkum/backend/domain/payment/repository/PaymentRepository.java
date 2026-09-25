package com.gakkum.backend.domain.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByJobIdAndStatus(Long jobId, PaymentStatus status);

    Optional<Payment> findByJobIdAndStatus(Long jobId, PaymentStatus status);

    Optional<JobIdProjection> findProjectedByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByOrderId(String orderId);

    interface JobIdProjection {
        Long getJobId();
    }
}
