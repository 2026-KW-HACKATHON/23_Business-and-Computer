package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
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
import com.gakkum.backend.global.exception.GlobalExceptionHandler;
import com.gakkum.backend.global.transaction.ImmediateTransactionTemplate;

@DisplayName("사장님 완료 요청 전체 흐름 (POST /jobs/{jobId}/submissions/{submissionId}/complete)")
class JobCompleteFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final String JOB_TITLE = "메뉴판 디자인";
    private static final Instant NOW = Instant.parse("2026-09-28T03:15:30Z");
    private static final String URL = "/jobs/42/submissions/81/complete";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, mock(JwtService.class));
        JobService jobService = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
                mock(JobApplicationRepository.class), jobSubmissionRepository,
                Clock.fixed(NOW, ZoneId.of("UTC")));
        JobFacade facade = new JobFacade(userService, new OwnerService(ownerRepository), jobService,
                mock(SpecialtyCategoryService.class), mock(SpecialtyService.class), new StudentService(studentRepository),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class), paymentService,
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class), eventPublisher,
                new ImmediateTransactionTemplate(), mock(ChatRoomService.class));
        when(studentRepository.findById(7L)).thenReturn(Optional.of(
                Student.builder().id(7L).userId(STUDENT_USER_ID).build()));
        when(userRepository.findById(STUDENT_USER_ID)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).name("김학생").role(UserRole.STUDENT).build()));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("본인 의뢰의 검토 대기 제출물을 완료하면 200과 data 없는 성공 응답을 반환하고 의뢰를 종료한 뒤 후기 요청과 정산 내역을 알린다")
    void completesSubmission() throws Exception {
        givenActiveOwner();
        Job job = givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.PENDING);
        when(paymentService.findPaidPayment(42L)).thenReturn(Optional.of(
                new ApprovedPaymentData(91L, "order-123", 100_000L, NOW)));

        mockMvc.perform(post(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.APPROVED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(job.getCompletedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(publishedEvents()).containsExactly(
                NotificationEventFactory.jobReviewRequested(OWNER_USER_ID, 42L, JOB_TITLE, "김학생"),
                NotificationEventFactory.paymentSettled(STUDENT_USER_ID, 91L, 42L, JOB_TITLE, 100_000L));
    }

    @Test
    @DisplayName("결제 완료 기록이 없는 의뢰를 완료하면 후기 요청만 알리고 정산 내역은 알리지 않는다")
    void doesNotNotifySettlementWithoutPaidPayment() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        givenSubmission(JobSubmissionReviewStatus.PENDING);

        mockMvc.perform(post(URL).principal(authentication)).andExpect(status().isOk());

        assertThat(publishedEvents()).containsExactly(
                NotificationEventFactory.jobReviewRequested(OWNER_USER_ID, 42L, JOB_TITLE, "김학생"));
    }

    @Test
    @DisplayName("이미 완료된 의뢰를 다시 완료하면 409 JOB_SUBMISSION_409_REVIEW_STATUS를 반환한다")
    void rejectsDuplicateComplete() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.CLOSED);
        givenSubmission(JobSubmissionReviewStatus.APPROVED);

        mockMvc.perform(post(URL).principal(authentication))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_409_REVIEW_STATUS"));
        verifyNoInteractions(eventPublisher, paymentService);
    }

    @Test
    @DisplayName("수정 요청된 제출물을 완료하면 409 JOB_SUBMISSION_409_REVIEWED를 반환한다")
    void rejectsRevisionRequestedSubmission() throws Exception {
        givenActiveOwner();
        Job job = givenOwnedJob(JobStatus.MATCHED);
        givenSubmission(JobSubmissionReviewStatus.REVISION_REQUESTED);

        mockMvc.perform(post(URL).principal(authentication))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_409_REVIEWED"));
        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        verifyNoInteractions(eventPublisher, paymentService);
    }

    @Test
    @DisplayName("다른 사업주의 의뢰이면 404 JOB_404를 반환한다")
    void rejectsOtherOwnersJob() throws Exception {
        givenActiveOwner();
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.empty());

        mockMvc.perform(post(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("존재하지 않는 제출물이면 404 JOB_SUBMISSION_404를 반환한다")
    void rejectsMissingSubmission() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        when(jobSubmissionRepository.findById(81L)).thenReturn(Optional.empty());

        mockMvc.perform(post(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_404"));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("사업주 프로필이 없는 사용자(학생 포함)는 403 OWNER_403으로 거부하고 의뢰를 조회하지 않는다")
    void rejectsUserWithoutOwnerProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.STUDENT).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post(URL).principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OWNER_403"));
        verifyNoInteractions(jobRepository, jobSubmissionRepository, eventPublisher);
    }

    @Test
    @DisplayName("의뢰 ID나 제출물 ID가 0 이하이면 400을 반환하고 조회하지 않는다")
    void rejectsNonPositiveIds() throws Exception {
        mockMvc.perform(post("/jobs/42/submissions/0/complete").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        mockMvc.perform(post("/jobs/0/submissions/81/complete").principal(authentication))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userRepository, jobRepository, jobSubmissionRepository);
    }

    private void givenActiveOwner() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.of(
                Owner.builder().id(5L).userId(OWNER_USER_ID).storeName("가꿈 카페").build()));
    }

    private Job givenOwnedJob(JobStatus status) {
        Job job = Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .title(JOB_TITLE)
                .status(status)
                .selectedStudentProfileId(7L)
                .revisionCount(2)
                .build();
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(job));
        return job;
    }

    private JobSubmission givenSubmission(JobSubmissionReviewStatus reviewStatus) {
        JobSubmission submission = JobSubmission.builder()
                .id(81L)
                .jobId(42L)
                .submissionType(JobSubmissionType.DRAFT)
                .revisionNumber(0)
                .reviewStatus(reviewStatus)
                .build();
        when(jobSubmissionRepository.findById(81L)).thenReturn(Optional.of(submission));
        return submission;
    }

    private List<NotificationEvent> publishedEvents() {
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
    }
}
