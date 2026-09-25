package com.gakkum.backend.domain.payment.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public Payment preparePayment(Job job, JobApplication application, String ownerUserId) {

        // 예외: 의뢰의 가격이 없거나 0 이하인 경우
        if (job.getBudget() == null || job.getBudget() <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        // 예외: 이미 결제가 완료된 의뢰(Job)인 경우
        if (paymentRepository.existsByJobIdAndStatus(job.getId(), PaymentStatus.PAID)) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID);
        }

        // 이미 PENDING 상태의 결제 시도가 있다면(현재가 재시도) 이전 요청을 무효 처리
        paymentRepository.findByJobIdAndStatus(job.getId(), PaymentStatus.PENDING)
                .ifPresent(previous -> {
                    previous.supersede();
                    paymentRepository.flush();
                });

        Payment payment = Payment.pending(
                job.getId(),
                application.getId(),
                ownerUserId,
                UUID.randomUUID().toString(),
                job.getBudget(),
                Instant.now(clock));

        return paymentRepository.save(payment);
    }
}
