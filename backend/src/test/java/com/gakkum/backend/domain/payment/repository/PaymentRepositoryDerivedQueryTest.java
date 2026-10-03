package com.gakkum.backend.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;

@SpringBootTest
@ActiveProfiles("local")
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

    @Test
    @DisplayName("본인의 PAID·REFUNDED 결제만 승인 시각 최신순, 같은 시각은 ID 내림차순으로 조회한다")
    void findsOwnApprovedPaymentsInLatestOrder() {
        String owner = "01K58M6PJV8VAJMXHBHJ2PNH01";
        String other = "01K58M6PJV8VAJMXHBHJ2PNH02";
        Instant earlier = Instant.parse("2026-09-30T14:59:59Z");
        Instant later = Instant.parse("2026-09-30T15:00:00Z");

        Payment oldPaid = approved(900_001L, owner, "history-old-paid", "THISTORY000000000001", earlier);
        Payment refunded = approved(900_002L, owner, "history-refunded", "THISTORY000000000002", later);
        refunded.refundOnCancel(Instant.parse("2026-10-05T00:00:00Z"));
        Payment sameTimePaid = approved(900_003L, owner, "history-same-time", "THISTORY000000000003", later);
        approved(900_004L, other, "history-other-owner", "THISTORY000000000004", later);
        paymentRepository.save(Payment.pending(900_005L, 201L, owner, "history-pending", 100_000L, Instant.EPOCH));
        Payment readyFailed = Payment.pending(900_006L, 201L, owner, "history-ready-failed", 100_000L, Instant.EPOCH);
        readyFailed.failReady();
        paymentRepository.save(readyFailed);
        Payment superseded = Payment.pending(900_007L, 201L, owner, "history-superseded", 100_000L, Instant.EPOCH);
        superseded.supersede();
        paymentRepository.save(superseded);
        paymentRepository.flush();

        List<Payment> history = paymentRepository.findByOwnerUserIdAndStatusInOrderByApprovedAtDescIdDesc(
                owner, List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED));

        assertThat(history).extracting(Payment::getOrderId)
                .containsExactly("history-same-time", "history-refunded", "history-old-paid");
        assertThat(history).extracting(Payment::getStatus)
                .containsExactly(PaymentStatus.PAID, PaymentStatus.REFUNDED, PaymentStatus.PAID);
        assertThat(sameTimePaid.getId()).isGreaterThan(refunded.getId());
        assertThat(history.get(1).getRefundAmount()).isEqualTo(80_000L);
        assertThat(history.get(2)).isEqualTo(oldPaid);
    }

    private Payment approved(Long jobId, String ownerUserId, String orderId, String tid, Instant approvedAt) {
        Payment payment = Payment.pending(jobId, 201L, ownerUserId, orderId, 100_000L, Instant.EPOCH);
        payment.recordKakaoTid(tid);
        payment.approve(approvedAt);
        return paymentRepository.save(payment);
    }
}
