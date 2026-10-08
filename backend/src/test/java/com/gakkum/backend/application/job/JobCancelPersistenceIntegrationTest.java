package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.chat.repository.ChatRoomRepository;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CancelJobCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobCancelResult;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.notification.client.NotificationEventPublisher;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;
import com.gakkum.backend.domain.notification.dto.NotificationEventFactory;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
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
    private JobSubmissionRepository jobSubmissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    // 실제 커밋·롤백에 따른 발행 여부만 확인하고 로컬 Redis에는 테스트 이벤트를 남기지 않는다
    @MockitoBean
    private NotificationEventPublisher notificationEventPublisher;

    private final List<Long> jobIds = new ArrayList<>();
    private final List<Long> paymentIds = new ArrayList<>();
    private String username;
    private String userId;
    private Long ownerProfileId;
    private Long studentProfileId;
    private String studentUserId;

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
        // 진행 중 의뢰의 담당 학생. 취소 알림의 수신자다
        studentUserId = ("S" + unique).substring(0, 26);
        studentProfileId = studentRepository.saveAndFlush(Student.create(
                studentUserId, "테스트대", "TEST-" + unique, "테스트 전공", null, null, null)).getId();
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            paymentRepository.deleteAllById(paymentIds);
            jobSubmissionRepository.deleteAll(jobSubmissionRepository.findByJobIdIn(jobIds));
            jobIds.forEach(jobId -> chatRoomRepository.findByJobId(jobId).ifPresent(chatRoomRepository::delete));
            jobRepository.deleteAllById(jobIds);
            studentRepository.deleteById(studentProfileId);
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
    @DisplayName("PostgreSQL에서 진행 중 의뢰를 취소하면 취소 입력과 결제 환불 기록이 함께 저장되고 커밋 뒤 담당 학생과 사장님에게 알림을 발행한다")
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
        String chatRoomId = chatRoomRepository.findByJobId(job.getId()).orElseThrow().getId();
        ArgumentCaptor<NotificationEvent> events = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventPublisher, times(2)).publishCommitted(events.capture());
        assertThat(events.getAllValues()).containsExactly(
                NotificationEventFactory.jobCancelledByOwner(
                        studentUserId, job.getId(), chatRoomId, "취소 테스트 의뢰", "취소 테스트 매장"),
                NotificationEventFactory.paymentRefunded(userId, payment.getId(), "취소 테스트 의뢰", 80_000L));
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
        // 롤백된 취소는 알림을 발행하지 않는다
        verify(notificationEventPublisher, never()).publishCommitted(any());
    }

    /** 제출 이후 취소를 시도하는 시점의 제출 이력. */
    enum SubmittedStage {
        DRAFT_PENDING, REVISION_REQUESTED, REVISION_PENDING
    }

    @ParameterizedTest
    @EnumSource(SubmittedStage.class)
    @DisplayName("PostgreSQL에서 제출 이력이 있는 진행 중 의뢰의 취소는 검토 상태와 관계없이 거부되고 의뢰·결제·제출물이 그대로 남는다")
    void rejectsCancellationAfterSubmissionAndKeepsEverything(SubmittedStage stage) {
        Job job = saveJob(true);
        Payment payment = savePaidPayment(job.getId(), 100_000L);
        List<JobSubmissionReviewStatus> reviewStatuses = saveSubmissions(job.getId(), stage);

        assertThatThrownBy(() -> jobFacade.cancelJob(command(job.getId())))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOB_CANCEL_SUBMITTED));

        Job found = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(found.getCompletedAt()).isNull();
        assertThat(found.getCancelReason()).isNull();
        assertThat(found.getMessageToStudent()).isNull();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(foundPayment.getRefundedAt()).isNull();
        assertThat(jobSubmissionRepository.findByJobIdOrderByRevisionNumberAsc(job.getId()))
                .extracting(JobSubmission::getReviewStatus)
                .containsExactlyElementsOf(reviewStatuses);
    }

    private List<JobSubmissionReviewStatus> saveSubmissions(Long jobId, SubmittedStage stage) {
        JobSubmission draft = JobSubmission.create(
                jobId, JobSubmissionType.DRAFT, 0, List.of("https://example.com/draft.png"), "초안입니다.");
        if (stage == SubmittedStage.DRAFT_PENDING) {
            jobSubmissionRepository.saveAndFlush(draft);
            return List.of(JobSubmissionReviewStatus.PENDING);
        }
        draft.requestRevision(LocalDateTime.now(), "로고를 조금 더 크게 해주세요.", List.of());
        jobSubmissionRepository.saveAndFlush(draft);
        if (stage == SubmittedStage.REVISION_REQUESTED) {
            return List.of(JobSubmissionReviewStatus.REVISION_REQUESTED);
        }
        jobSubmissionRepository.saveAndFlush(JobSubmission.create(
                jobId, JobSubmissionType.REVISION, 1, List.of("https://example.com/revision.png"), "수정안입니다."));
        return List.of(JobSubmissionReviewStatus.REVISION_REQUESTED, JobSubmissionReviewStatus.PENDING);
    }

    private CancelJobCommand command(Long jobId) {
        return CancelJobCommand.of(username, jobId, CANCEL_REASON, MESSAGE_TO_STUDENT);
    }

    private Job saveJob(boolean matched) {
        Job job = Job.create(ownerProfileId, "취소 테스트 의뢰", "설명", 100_000L,
                LocalDate.now(), LocalDate.now().plusDays(3), 2, null);
        if (matched) {
            job.match(studentProfileId);
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
