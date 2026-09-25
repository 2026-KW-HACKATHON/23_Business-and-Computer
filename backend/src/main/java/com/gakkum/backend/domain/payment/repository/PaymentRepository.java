package com.gakkum.backend.domain.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByJobIdAndStatus(Long jobId, PaymentStatus status);

    Optional<Payment> findByJobIdAndStatus(Long jobId, PaymentStatus status);
}
