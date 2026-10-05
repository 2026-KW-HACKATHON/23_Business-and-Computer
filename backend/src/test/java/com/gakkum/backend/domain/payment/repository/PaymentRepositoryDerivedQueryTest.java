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
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 0, null));
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

    @Test
    @DisplayName("의뢰 ID 목록에 연결된 PAID·REFUNDED 결제만 승인 시각 최신순, 같은 시각은 ID 내림차순으로 조회한다")
    void findsApprovedPaymentsOfJobsInLatestOrder() {
        String owner = "01K58M6PJV8VAJMXHBHJ2PNH03";
        Instant earlier = Instant.parse("2026-09-30T14:59:59Z");
        Instant later = Instant.parse("2026-09-30T15:00:00Z");

        Payment refunded = approved(900_012L, 910_002L, owner, "settle-refunded", "TSETTLE0000000000002", later);
        refunded.refundOnCancel(Instant.parse("2026-10-05T00:00:00Z"));
        Payment sameTimePaid = approved(900_013L, 910_003L, owner, "settle-same-time", "TSETTLE0000000000003",
                later);
        // 같은 사장님이 결제했지만 다른 학생이 담당하는 의뢰
        approved(900_014L, 910_099L, owner, "settle-other-student", "TSETTLE0000000000004", later);
        paymentRepository.save(Payment.pending(900_015L, 910_001L, owner, "settle-pending", 100_000L,
                Instant.EPOCH));
        Payment readyFailed = Payment.pending(900_016L, 910_002L, owner, "settle-ready-failed", 100_000L,
                Instant.EPOCH);
        readyFailed.failReady();
        paymentRepository.save(readyFailed);
        Payment superseded = Payment.pending(900_017L, 910_003L, owner, "settle-superseded", 100_000L,
                Instant.EPOCH);
        superseded.supersede();
        paymentRepository.save(superseded);
        // 가장 오래된 승인 건을 마지막에 저장해 ID 순서와 승인 순서를 어긋나게 한다
        Payment oldPaid = approved(900_011L, 910_001L, owner, "settle-old-paid", "TSETTLE0000000000001", earlier);
        paymentRepository.flush();

        List<Payment> history = paymentRepository.findByJobIdInAndStatusInOrderByApprovedAtDescIdDesc(
                List.of(900_011L, 900_012L, 900_013L, 900_015L, 900_016L, 900_017L, 900_018L),
                List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED));

        assertThat(history).extracting(Payment::getOrderId)
                .containsExactly("settle-same-time", "settle-refunded", "settle-old-paid");
        assertThat(history).extracting(Payment::getStatus)
                .containsExactly(PaymentStatus.PAID, PaymentStatus.REFUNDED, PaymentStatus.PAID);
        assertThat(sameTimePaid.getId()).isGreaterThan(refunded.getId());
        assertThat(oldPaid.getId()).isGreaterThan(sameTimePaid.getId());
        assertThat(history.get(1).getStudentCompensationAmount()).isEqualTo(20_000L);
        assertThat(history.get(2)).isEqualTo(oldPaid);
    }

    private Payment approved(Long jobId, String ownerUserId, String orderId, String tid, Instant approvedAt) {
        return approved(jobId, 201L, ownerUserId, orderId, tid, approvedAt);
    }

    private Payment approved(Long jobId, Long jobApplicationId, String ownerUserId, String orderId, String tid,
            Instant approvedAt) {
        Payment payment = Payment.pending(jobId, jobApplicationId, ownerUserId, orderId, 100_000L, Instant.EPOCH);
        payment.recordKakaoTid(tid);
        payment.approve(approvedAt);
        return paymentRepository.save(payment);
    }
}
