package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;
import com.gakkum.backend.domain.notification.dto.NotificationEventFactory;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedPaymentData;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.transaction.ImmediateTransactionTemplate;

@DisplayName("미확인 제출물 자동 완료 전체 흐름 (완료 처리와 후기 요청·정산 내역 알림)")
class JobAutoCompleteFlowTest {

    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final String JOB_TITLE = "메뉴판 디자인";
    private static final Instant NOW = Instant.parse("2026-09-28T03:15:30Z");
    private static final LocalDateTime REFERENCE_TIME = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private JobFacade facade;

    @BeforeEach
    void setUp() {
        JobService jobService = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
                mock(JobApplicationRepository.class), jobSubmissionRepository,
                Clock.fixed(NOW, ZoneId.of("UTC")));
        facade = new JobFacade(new UserService(userRepository, mock(JwtService.class)),
                new OwnerService(ownerRepository), jobService,
                mock(SpecialtyCategoryService.class), mock(SpecialtyService.class), new StudentService(studentRepository),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class), paymentService,
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class),
                mock(MediaService.class), eventPublisher, new ImmediateTransactionTemplate(),
                mock(ChatRoomService.class));
        when(ownerRepository.findById(5L)).thenReturn(Optional.of(
                Owner.builder().id(5L).userId(OWNER_USER_ID).storeName("가꿈 카페").build()));
        when(studentRepository.findById(7L)).thenReturn(Optional.of(
                Student.builder().id(7L).userId(STUDENT_USER_ID).build()));
        when(userRepository.findById(STUDENT_USER_ID)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).name("김학생").role(UserRole.STUDENT).build()));
    }

    @Test
    @DisplayName("기간이 지난 의뢰를 자동 완료하면 수동 완료와 같은 후기 요청과 정산 내역 알림을 발행한다")
    void completesAndNotifiesLikeManualCompletion() {
        Job job = givenJob();
        JobSubmission submission = givenLatestSubmission(REFERENCE_TIME.minusHours(168));
        when(paymentService.findPaidPayment(42L)).thenReturn(Optional.of(
                new ApprovedPaymentData(91L, "order-123", 100_000L, NOW)));

        assertThat(facade.autoCompleteSubmission(42L, REFERENCE_TIME)).isTrue();

        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.APPROVED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(publishedEvents()).containsExactly(
                NotificationEventFactory.jobReviewRequested(OWNER_USER_ID, 42L, JOB_TITLE, "김학생"),
                NotificationEventFactory.paymentSettled(STUDENT_USER_ID, 91L, 42L, JOB_TITLE, 100_000L));
    }

    @Test
    @DisplayName("결제 완료 기록이 없는 의뢰를 자동 완료하면 후기 요청만 알리고 정산 내역은 알리지 않는다")
    void doesNotNotifySettlementWithoutPaidPayment() {
        givenJob();
        givenLatestSubmission(REFERENCE_TIME.minusHours(169));

        assertThat(facade.autoCompleteSubmission(42L, REFERENCE_TIME)).isTrue();

        assertThat(publishedEvents()).containsExactly(
                NotificationEventFactory.jobReviewRequested(OWNER_USER_ID, 42L, JOB_TITLE, "김학생"));
    }

    @Test
    @DisplayName("조건이 바뀌어 건너뛴 의뢰는 알림을 준비하지 않는다")
    void doesNotNotifySkippedJob() {
        Job job = givenJob();
        givenLatestSubmission(REFERENCE_TIME.minusHours(167));

        assertThat(facade.autoCompleteSubmission(42L, REFERENCE_TIME)).isFalse();

        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        verifyNoInteractions(eventPublisher, paymentService, ownerRepository, studentRepository);
    }

    private Job givenJob() {
        Job job = Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .title(JOB_TITLE)
                .status(JobStatus.MATCHED)
                .selectedStudentProfileId(7L)
                .revisionCount(2)
                .build();
        when(jobRepository.findLockedById(42L)).thenReturn(Optional.of(job));
        return job;
    }

    private JobSubmission givenLatestSubmission(LocalDateTime createdAt) {
        JobSubmission submission = JobSubmission.builder()
                .id(81L)
                .jobId(42L)
                .submissionType(JobSubmissionType.DRAFT)
                .revisionNumber(0)
                .reviewStatus(JobSubmissionReviewStatus.PENDING)
                .createdAt(createdAt)
                .build();
        when(jobSubmissionRepository.findFirstByJobIdOrderByRevisionNumberDesc(42L))
                .thenReturn(Optional.of(submission));
        return submission;
    }

    private List<NotificationEvent> publishedEvents() {
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
    }
}
