package com.gakkum.backend.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.payment.entity.Payment;

@SpringBootTest
@Transactional
class PaymentRepositoryDerivedQueryTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("주문번호로 의뢰 ID를 투영하고 해당 의뢰를 잠금 조회한다")
    void projectsJobIdAndLocksJob() {
        Job job = jobRepository.saveAndFlush(Job.create(101L, "결제 테스트 의뢰", "설명", 100_000L,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 0));
        paymentRepository.saveAndFlush(Payment.pending(job.getId(), 201L,
                "01K58M6PJV8VAJMXHBHJ2PNB5C", "order-derived-query", 100_000L, Instant.EPOCH));

        Long jobId = paymentRepository.findProjectedByOrderId("order-derived-query")
                .orElseThrow().getJobId();

        assertThat(jobId).isEqualTo(job.getId());
        assertThat(jobRepository.findLockedById(jobId)).contains(job);
    }
}
