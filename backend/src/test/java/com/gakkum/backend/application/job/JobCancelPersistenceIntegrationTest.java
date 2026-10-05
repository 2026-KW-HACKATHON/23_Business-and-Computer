package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CancelJobCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobCancelResult;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

/** 취소 실패 시 롤백은 Facade 트랜잭션이 실제로 커밋·롤백되어야 확인되므로 클래스 트랜잭션 대신 직접 데이터를 정리한다. */
@SpringBootTest
@ActiveProfiles("local")
class JobCancelPersistenceIntegrationTest {

    private static final String CANCEL_REASON = "매장 일정이 변경되어\n작업이 필요 없어졌습니다.";
    private static final String MESSAGE_TO_STUDENT = "진행해 주셔서 감사합니다.";

    @Autowired
    private JobFacade jobFacade;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> jobIds = new ArrayList<>();
    private final List<Long> paymentIds = new ArrayList<>();
    private String username;
    private String userId;
    private Long ownerProfileId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        userId = unique.substring(0, 26);
        username = "TEST_CANCEL_" + unique;
        userRepository.saveAndFlush(User.builder()
                .id(userId).username(username).isLock(false).role(UserRole.OWNER).build());
        ownerProfileId = ownerRepository.saveAndFlush(Owner.builder()
                .userId(userId).businessNumber("TEST-" + unique).storeName("취소 테스트 매장").categoryId(1L).build())
                .getId();
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            paymentRepository.deleteAllById(paymentIds);
            jobRepository.deleteAllById(jobIds);
            ownerRepository.deleteById(ownerProfileId);
            userRepository.deleteById(userId);
        });
    }

    @Test
    @DisplayName("PostgreSQL에서 모집 중 의뢰를 취소하면 상태·시각·취소 이유·남길 말이 함께 저장되고 재조회된다")
    void persistsCancellationDetailsForOpenJob() {
        Job job = saveJob(false);

        JobCancelResult result = jobFacade.cancelJob(command(job.getId()));

        Job found = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(found.getCompletedAt()).isNotNull();
        assertThat(found.getCancelReason()).isEqualTo(CANCEL_REASON);
        assertThat(found.getMessageToStudent()).isEqualTo(MESSAGE_TO_STUDENT);
        assertThat(result.getCancelReason()).isEqualTo(CANCEL_REASON);
        assertThat(result.getMessageToStudent()).isEqualTo(MESSAGE_TO_STUDENT);
        assertThat(result.getRefundAmount()).isZero();
    }

    @Test
    @DisplayName("PostgreSQL에서 진행 중 의뢰를 취소하면 취소 입력과 결제 환불 기록이 함께 저장된다")
    void persistsCancellationDetailsAndRefundForMatchedJob() {
        Job job = saveJob(true);
        Payment payment = savePaidPayment(job.getId(), 100_000L);

        JobCancelResult result = jobFacade.cancelJob(command(job.getId()));

        Job found = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(found.getCancelReason()).isEqualTo(CANCEL_REASON);
        assertThat(found.getMessageToStudent()).isEqualTo(MESSAGE_TO_STUDENT);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(result.getStudentCompensationAmount()).isEqualTo(20_000L);
        assertThat(result.getRefundAmount()).isEqualTo(80_000L);
    }

    @Test
    @DisplayName("PostgreSQL에서 진행 중 의뢰의 결제 내역이 없어 취소가 실패하면 상태·시각·취소 이유·남길 말이 모두 롤백된다")
    void rollsBackCancellationWhenPaymentIsMissing() {
        Job job = saveJob(true);

        assertThatThrownBy(() -> jobFacade.cancelJob(command(job.getId())))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));

        Job found = transactionTemplate.execute(status -> jobRepository.findById(job.getId()).orElseThrow());
        assertThat(found.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(found.getCompletedAt()).isNull();
        assertThat(found.getCancelReason()).isNull();
        assertThat(found.getMessageToStudent()).isNull();
    }

    private CancelJobCommand command(Long jobId) {
        return CancelJobCommand.of(username, jobId, CANCEL_REASON, MESSAGE_TO_STUDENT);
    }

    private Job saveJob(boolean matched) {
        Job job = Job.create(ownerProfileId, "취소 테스트 의뢰", "설명", 100_000L,
                LocalDate.now(), LocalDate.now().plusDays(3), 2, null);
        if (matched) {
            job.match(7L);
        }
        Job saved = jobRepository.saveAndFlush(job);
        jobIds.add(saved.getId());
        return saved;
    }

    private Payment savePaidPayment(Long jobId, Long amount) {
        Instant now = Instant.now();
        Payment payment = Payment.pending(jobId, 21L, userId, UUID.randomUUID().toString(), amount, now);
        payment.recordKakaoTid("T1234567890123456789");
        payment.approve(now);
        Payment saved = paymentRepository.saveAndFlush(payment);
        paymentIds.add(saved.getId());
        return saved;
    }
}
