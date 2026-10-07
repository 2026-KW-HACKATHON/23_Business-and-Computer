package com.gakkum.backend.domain.student.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Limit;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.repository.ReviewRepository;
import com.gakkum.backend.domain.review.service.ReviewService;

import jakarta.persistence.EntityManager;

/**
 * 연관관계 없는 조인과 조회 개수 제한은 PostgreSQL에서만 확인할 수 있어 실제 DB로 검증한다. 받은 리뷰 전체 조회도 같은 DB로 확인한다.
 * 공용 DB에 테스트 행을 쓰지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 트랜잭션은 테스트마다 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("학생 내 정보 PostgreSQL 조회 (받은 리뷰 전체의 조건·정렬, 정산 완료 내역의 조건·정렬·개수 제한, 취소 제외 제안 수)")
class StudentMePostgresTest {

    private static final long STUDENT_PROFILE_ID = 989_101L;
    private static final long OTHER_STUDENT_PROFILE_ID = 989_102L;
    private static final long OWNER_PROFILE_ID = 989_001L;
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2OWNER";
    private static final LocalDateTime T1 = LocalDateTime.of(2031, 1, 1, 9, 0, 0, 123_456_000);
    private static final LocalDateTime T2 = LocalDateTime.of(2031, 1, 2, 9, 0, 0, 123_456_000);
    private static final LocalDateTime T3 = LocalDateTime.of(2031, 1, 3, 9, 0, 0, 123_456_000);
    private static final LocalDateTime T4 = LocalDateTime.of(2031, 1, 4, 9, 0, 0, 123_456_000);
    private static final Instant APPROVED_EARLY = Instant.parse("2030-12-01T00:00:00Z");
    private static final Instant APPROVED_LATE = Instant.parse("2030-12-20T00:00:00Z");

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("리뷰는 본인이 받은 것만 개수 제한 없이 작성 시각 내림차순, 같은 시각은 리뷰 ID 내림차순으로 읽고 전체 개수는 따로 센다")
    void readsAllOwnReviews() {
        Long oldest = review(STUDENT_PROFILE_ID, T1);
        Long tiedLowerId = review(STUDENT_PROFILE_ID, T2);
        Long tiedHigherId = review(STUDENT_PROFILE_ID, T2);
        Long latest = review(STUDENT_PROFILE_ID, T3);
        review(OTHER_STUDENT_PROFILE_ID, T4);
        entityManager.clear();

        assertThat(studentReviewIds()).containsExactly(latest, tiedHigherId, tiedLowerId, oldest);
        assertThat(reviewRepository.countByStudentProfileId(STUDENT_PROFILE_ID)).isEqualTo(4);
        assertThat(reviewRepository.findAverageRatingByStudentProfileId(STUDENT_PROFILE_ID)).isEqualTo(4.0);
    }

    @Test
    @DisplayName("작성 시각이 없는 리뷰는 리뷰 ID가 더 커도 마지막에 두고, 그 안에서는 리뷰 ID 내림차순이다")
    void putsReviewsWithoutCreatedAtLast() {
        Long dated = review(STUDENT_PROFILE_ID, T1);
        Long undatedLowerId = review(STUDENT_PROFILE_ID, null);
        Long undatedHigherId = review(STUDENT_PROFILE_ID, null);
        Long latest = review(STUDENT_PROFILE_ID, T2);
        entityManager.clear();

        assertThat(studentReviewIds()).containsExactly(latest, dated, undatedHigherId, undatedLowerId);
    }

    @Test
    @DisplayName("정산 완료 내역은 결제 승인 순서가 아닌 의뢰 완료 시각 내림차순, 같은 시각은 의뢰 ID 내림차순으로 세 개까지 읽는다")
    void readsLatestThreeSettledPaymentsByCompletedAt() {
        // 가장 늦게 승인된 결제의 의뢰가 가장 먼저 완료됐다
        Long completedFirst = job(STUDENT_PROFILE_ID, JobStatus.CLOSED, T1, null);
        paid(completedFirst, APPROVED_LATE);
        Long tiedLowerId = job(STUDENT_PROFILE_ID, JobStatus.CLOSED, T2, null);
        paid(tiedLowerId, APPROVED_EARLY.plusSeconds(60));
        Long tiedHigherId = job(STUDENT_PROFILE_ID, JobStatus.CLOSED, T2, null);
        paid(tiedHigherId, APPROVED_EARLY.plusSeconds(120));
        // 가장 먼저 승인된 결제의 의뢰가 가장 늦게 완료됐다
        Long completedLast = job(STUDENT_PROFILE_ID, JobStatus.CLOSED, T3, null);
        paid(completedLast, APPROVED_EARLY);
        entityManager.clear();

        assertThat(settledJobIds(Limit.of(3))).containsExactly(completedLast, tiedHigherId, tiedLowerId);
        assertThat(settledJobIds(Limit.of(10)))
                .containsExactly(completedLast, tiedHigherId, tiedLowerId, completedFirst);
    }

    @Test
    @DisplayName("정산 완료 내역은 일반·제안 의뢰를 함께 읽고 정산 예정·착수 보상·전액 환불·대체된 주문·다른 학생의 결제는 뺀다")
    void readsOnlyPaidPaymentsOfOwnClosedJobs() {
        Long applicationJob = job(STUDENT_PROFILE_ID, JobStatus.CLOSED, T1, null);
        Payment applicationPayment = paid(applicationJob, APPROVED_EARLY);
        // 같은 의뢰의 대체된(SUPERSEDED) 결제 시도는 정산이 아니다
        superseded(applicationJob);
        Long proposalJob = job(STUDENT_PROFILE_ID, JobStatus.CLOSED, T2, 989_501L);
        Payment proposalPayment = paidForProposal(989_501L, proposalJob, APPROVED_EARLY);

        // 정산 예정: 결제 완료, 의뢰 진행 중·작업 시작 대기
        paid(job(STUDENT_PROFILE_ID, JobStatus.MATCHED, null, null), APPROVED_LATE);
        paidForProposal(989_502L, job(STUDENT_PROFILE_ID, JobStatus.AWAITING_START, null, 989_502L), APPROVED_LATE);
        // 착수 보상: 진행 중 취소로 일부 환불
        Payment compensated = paid(job(STUDENT_PROFILE_ID, JobStatus.CANCELLED, T4, null), APPROVED_LATE);
        compensated.refundOnCancel(Instant.parse("2031-01-04T00:00:00Z"));
        // 환불: 학생의 의뢰서 거절로 전액 환불
        Payment declined = paidForProposal(
                989_503L, job(STUDENT_PROFILE_ID, JobStatus.CANCELLED, T4, 989_503L), APPROVED_LATE);
        declined.refundOnDecline(Instant.parse("2031-01-04T00:00:00Z"));
        // 다른 학생이 담당한 완료 의뢰
        paid(job(OTHER_STUDENT_PROFILE_ID, JobStatus.CLOSED, T4, null), APPROVED_LATE);
        paymentRepository.flush();
        entityManager.clear();

        List<Payment> settled = paymentRepository.findLatestCompletedByStudentProfileId(
                STUDENT_PROFILE_ID, JobStatus.CLOSED, PaymentStatus.PAID, Limit.of(10));

        assertThat(settled).extracting(Payment::getId)
                .containsExactly(proposalPayment.getId(), applicationPayment.getId());
        assertThat(settled).extracting(Payment::getJobId).containsExactly(proposalJob, applicationJob);
        assertThat(settled).extracting(Payment::getAmount).containsExactly(80000L, 50000L);
        assertThat(settled.get(0).getProposalId()).isEqualTo(989_501L);
        assertThat(settled.get(0).getJobApplicationId()).isNull();
        assertThat(settled.get(1).getProposalId()).isNull();
    }

    @Test
    @DisplayName("취소 제외 제안 수는 CANCELLED만 빼고 나머지 상태를 모두 세며, 기존 집계는 취소한 제안도 센다")
    void countsProposalsExcludingCancelled() {
        for (ProposalStatus status : ProposalStatus.values()) {
            proposal(STUDENT_PROFILE_ID, status);
        }
        proposal(STUDENT_PROFILE_ID, ProposalStatus.CANCELLED);
        proposal(OTHER_STUDENT_PROFILE_ID, ProposalStatus.PENDING);

        assertThat(proposalRepository.countByStudentProfileIdAndStatusNot(
                STUDENT_PROFILE_ID, ProposalStatus.CANCELLED)).isEqualTo(4);
        assertThat(proposalRepository.countByStudentProfileId(STUDENT_PROFILE_ID)).isEqualTo(6);
        assertThat(proposalRepository.countByStudentProfileIdAndStatusNot(989_199L, ProposalStatus.CANCELLED))
                .isZero();
    }

    // 내 정보가 쓰는 전체 조회 경로
    private List<Long> studentReviewIds() {
        return new ReviewService(reviewRepository).getStudentReviews(STUDENT_PROFILE_ID).stream()
                .map(Review::getId)
                .toList();
    }

    private List<Long> settledJobIds(Limit limit) {
        return paymentRepository.findLatestCompletedByStudentProfileId(
                        STUDENT_PROFILE_ID, JobStatus.CLOSED, PaymentStatus.PAID, limit).stream()
                .map(Payment::getJobId)
                .toList();
    }

    /** 리뷰는 의뢰당 하나라 리뷰마다 완료 의뢰를 만든다. 작성 시각은 저장 시 자동으로 채워지므로 저장한 뒤 네이티브 SQL로 바꾼다. */
    private Long review(long studentProfileId, LocalDateTime createdAt) {
        Long jobId = job(studentProfileId, JobStatus.CLOSED, T1, null);
        Long id = reviewRepository.saveAndFlush(
                Review.create(jobId, OWNER_PROFILE_ID, studentProfileId, List.of(), "좋았어요.", 4)).getId();
        entityManager.createNativeQuery("UPDATE reviews SET created_at = CAST(:createdAt AS timestamp) WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate();
        return id;
    }

    private Long job(long studentProfileId, JobStatus status, LocalDateTime completedAt, Long proposalId) {
        return jobRepository.saveAndFlush(Job.builder()
                .ownerProfileId(OWNER_PROFILE_ID)
                .title("내 정보 테스트 의뢰")
                .description("설명")
                .budget(50000L)
                .draftDeadline(LocalDate.of(2031, 2, 1))
                .finalDeadline(LocalDate.of(2031, 2, 10))
                .revisionCount(1)
                .status(status)
                .selectedStudentProfileId(studentProfileId)
                .completedAt(completedAt)
                .proposalId(proposalId)
                .build()).getId();
    }

    private Payment paid(Long jobId, Instant approvedAt) {
        Payment payment = Payment.pending(jobId, 989_900L + jobId, OWNER_USER_ID, UUID.randomUUID().toString(),
                50000L, approvedAt.minusSeconds(60));
        payment.recordKakaoTid(tid());
        payment.approve(approvedAt);
        return paymentRepository.saveAndFlush(payment);
    }

    private Payment paidForProposal(Long proposalId, Long jobId, Instant approvedAt) {
        Payment payment = Payment.pendingForProposal(proposalId, OWNER_USER_ID, UUID.randomUUID().toString(),
                80000L, 1, null, approvedAt.minusSeconds(60));
        payment.recordKakaoTid(tid());
        payment.approve(approvedAt);
        payment.linkJob(jobId);
        return paymentRepository.saveAndFlush(payment);
    }

    private void superseded(Long jobId) {
        Payment payment = Payment.pending(jobId, 989_900L + jobId, OWNER_USER_ID, UUID.randomUUID().toString(),
                50000L, APPROVED_EARLY.minusSeconds(120));
        payment.supersede();
        paymentRepository.saveAndFlush(payment);
    }

    private void proposal(long studentProfileId, ProposalStatus status) {
        proposalRepository.saveAndFlush(Proposal.builder()
                .studentProfileId(studentProfileId)
                .ownerProfileId(OWNER_PROFILE_ID)
                .title("내 정보 테스트 제안")
                .customerProblem("문제")
                .proposedSolution("해결")
                .workPlan("작업 계획")
                .proposedFee(80000L)
                .draftDays(3)
                .finalDays(7)
                .referenceImageUrls(List.of())
                .likeCount(0)
                .status(status)
                .build());
    }

    private static String tid() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
    }
}
