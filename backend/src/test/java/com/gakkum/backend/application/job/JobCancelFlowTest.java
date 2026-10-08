package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import java.util.TimeZone;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobStatus;
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
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
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

@DisplayName("사장님 의뢰 취소 전체 흐름 (POST /jobs/{jobId}/cancel)")
class JobCancelFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final String OTHER_STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5E";
    private static final String CHAT_ROOM_ID = "01K58M6PJV8VAJMXHBHJ2CHAT1";
    private static final String JOB_TITLE = "메뉴판 디자인";
    private static final String STORE_NAME = "가꿈 카페";
    private static final Instant NOW = Instant.parse("2026-09-29T03:15:30Z");
    private static final String URL = "/jobs/42/cancel";
    private static final String CANCEL_REASON = "매장 일정이 변경되어 작업이 필요 없어졌습니다.";
    private static final String MESSAGE_TO_STUDENT = "진행해 주셔서 감사합니다.";
    private static final String VALID_BODY = body(CANCEL_REASON, MESSAGE_TO_STUDENT);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final ChatRoomService chatRoomService = mock(ChatRoomService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        UserService userService = new UserService(userRepository, mock(JwtService.class));
        JobService jobService = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
                jobApplicationRepository, jobSubmissionRepository, clock);
        JobFacade facade = new JobFacade(userService, new OwnerService(ownerRepository), jobService,
                mock(SpecialtyCategoryService.class), mock(SpecialtyService.class), new StudentService(studentRepository),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class),
                new PaymentService(paymentRepository, clock),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class), eventPublisher,
                new ImmediateTransactionTemplate(), chatRoomService);
        when(studentRepository.findById(7L)).thenReturn(Optional.of(
                Student.builder().id(7L).userId(STUDENT_USER_ID).build()));
        ChatRoom chatRoom = ChatRoom.create(42L);
        ReflectionTestUtils.setField(chatRoom, "id", CHAT_ROOM_ID);
        when(chatRoomService.getOrCreate(42L)).thenReturn(chatRoom);
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = { "UTC", "Asia/Seoul", "America/New_York" })
    @DisplayName("JVM 기본 시간대가 달라도 취소 시각은 UTC로 남기고 응답은 같은 순간의 한국 시각과 +09:00으로 내린다")
    void cancelledAtIgnoresDefaultTimeZone(String defaultZone) throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(defaultZone));
        try {
            givenActiveOwner();
            Job job = givenOwnedJob(JobStatus.MATCHED);
            givenPaidPayment(100_000L);

            // 시계 UTC 03:15:30 → 한국 12:15:30
            mockMvc.perform(cancelRequest(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.cancelledAt").value("2026-09-29T12:15:30+09:00"));
            assertThat(job.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 9, 29, 3, 15, 30));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("진행 중 의뢰를 취소하면 의뢰는 CANCELLED, 결제는 REFUNDED가 되고 학생 보상금 20%를 뺀 금액을 환불 금액으로 반환한다")
    void cancelsMatchedJobAndRefunds() throws Exception {
        givenActiveOwner();
        Job job = givenOwnedJob(JobStatus.MATCHED);
        Payment payment = givenPaidPayment(100_000L);

        mockMvc.perform(cancelRequest(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(42))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paidAmount").value(100_000))
                .andExpect(jsonPath("$.data.studentCompensationAmount").value(20_000))
                .andExpect(jsonPath("$.data.refundAmount").value(80_000))
                .andExpect(jsonPath("$.data.cancelledAt").value("2026-09-29T12:15:30+09:00"))
                .andExpect(jsonPath("$.data.cancelReason").value(CANCEL_REASON))
                .andExpect(jsonPath("$.data.messageToStudent").value(MESSAGE_TO_STUDENT));
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.getCancelReason()).isEqualTo(CANCEL_REASON);
        assertThat(job.getMessageToStudent()).isEqualTo(MESSAGE_TO_STUDENT);
        assertThat(job.getCompletedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAt()).isEqualTo(NOW);
        // 담당 학생에게 취소를 채팅방 대상으로, 사장님에게 기록된 환불 금액을 결제 대상으로 알린다
        assertThat(publishedEvents()).containsExactly(
                NotificationEventFactory.jobCancelledByOwner(STUDENT_USER_ID, 42L, CHAT_ROOM_ID, JOB_TITLE, STORE_NAME),
                NotificationEventFactory.paymentRefunded(OWNER_USER_ID, 91L, JOB_TITLE, 80_000L));
        verifyNoInteractions(jobApplicationRepository);
    }

    @Test
    @DisplayName("제출 이력이 있는 진행 중 의뢰를 취소하면 409 JOB_409_CANCEL_SUBMITTED를 반환하고 의뢰와 결제를 건드리지 않는다")
    void rejectsMatchedJobWithSubmission() throws Exception {
        givenActiveOwner();
        Job job = givenOwnedJob(JobStatus.MATCHED);
        when(jobSubmissionRepository.existsByJobId(42L)).thenReturn(true);

        mockMvc.perform(cancelRequest(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("JOB_409_CANCEL_SUBMITTED"))
                .andExpect(jsonPath("$.error.message")
                        .value("결과물이 제출된 의뢰는 취소할 수 없습니다. 수정 요청 또는 완료 확인을 진행해 주세요."));
        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(job.getCompletedAt()).isNull();
        assertThat(job.getCancelReason()).isNull();
        assertThat(job.getMessageToStudent()).isNull();
        // 검토 상태로 걸러 읽지 않고 제출 이력 유무만 확인한다
        verify(jobSubmissionRepository).existsByJobId(42L);
        verifyNoMoreInteractions(jobSubmissionRepository);
        verifyNoInteractions(paymentRepository, eventPublisher);
    }

    @Test
    @DisplayName("모집 중 의뢰를 취소하면 결제 없이 CANCELLED가 되고 금액은 모두 0이다")
    void cancelsOpenJobWithoutRefund() throws Exception {
        givenActiveOwner();
        Job job = givenOwnedJob(JobStatus.OPEN);

        mockMvc.perform(cancelRequest(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paidAmount").value(0))
                .andExpect(jsonPath("$.data.studentCompensationAmount").value(0))
                .andExpect(jsonPath("$.data.refundAmount").value(0))
                .andExpect(jsonPath("$.data.cancelReason").value(CANCEL_REASON))
                .andExpect(jsonPath("$.data.messageToStudent").value(MESSAGE_TO_STUDENT));
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.getCancelReason()).isEqualTo(CANCEL_REASON);
        assertThat(job.getMessageToStudent()).isEqualTo(MESSAGE_TO_STUDENT);
        // 지원자가 없으면 알릴 대상도 없다
        verifyNoInteractions(paymentRepository, eventPublisher, chatRoomService);
    }

    @Test
    @DisplayName("모집 중 의뢰를 취소하면 대기 중 지원자 전체에게 모집 취소를 알리고 지원서 상태와 결제는 건드리지 않는다")
    void notifiesPendingApplicantsWhenOpenJobIsCancelled() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.OPEN);
        JobApplication first = pendingApplication(301L, 7L);
        JobApplication second = pendingApplication(302L, 8L);
        when(jobApplicationRepository.findByJobIdInAndStatus(List.of(42L), JobApplicationStatus.PENDING))
                .thenReturn(List.of(first, second));
        when(studentRepository.findAllById(List.of(7L, 8L))).thenReturn(List.of(
                Student.builder().id(7L).userId(STUDENT_USER_ID).build(),
                Student.builder().id(8L).userId(OTHER_STUDENT_USER_ID).build()));

        mockMvc.perform(cancelRequest(VALID_BODY)).andExpect(status().isOk());

        assertThat(publishedEvents()).containsExactly(
                NotificationEventFactory.jobRecruitmentCancelled(STUDENT_USER_ID, 42L, JOB_TITLE, STORE_NAME),
                NotificationEventFactory.jobRecruitmentCancelled(OTHER_STUDENT_USER_ID, 42L, JOB_TITLE, STORE_NAME));
        assertThat(first.getStatus()).isEqualTo(JobApplicationStatus.PENDING);
        assertThat(second.getStatus()).isEqualTo(JobApplicationStatus.PENDING);
        verifyNoInteractions(paymentRepository, chatRoomService);
    }

    @Test
    @DisplayName("입력의 앞뒤 공백은 제거하고 내부 공백과 줄바꿈은 유지해 저장·반환한다")
    void trimsInputsAndKeepsInnerLineBreaks() throws Exception {
        givenActiveOwner();
        Job job = givenOwnedJob(JobStatus.OPEN);

        mockMvc.perform(cancelRequest(body("  일정 변경\n\n작업  불필요 \n", "\t감사합니다.\n다음에 또 봬요.  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelReason").value("일정 변경\n\n작업  불필요"))
                .andExpect(jsonPath("$.data.messageToStudent").value("감사합니다.\n다음에 또 봬요."));
        assertThat(job.getCancelReason()).isEqualTo("일정 변경\n\n작업  불필요");
        assertThat(job.getMessageToStudent()).isEqualTo("감사합니다.\n다음에 또 봬요.");
    }

    @Test
    @DisplayName("두 입력이 각각 5,000자이면 취소를 허용한다")
    void acceptsMaxLengthInputs() throws Exception {
        givenActiveOwner();
        Job job = givenOwnedJob(JobStatus.OPEN);

        mockMvc.perform(cancelRequest(body("가".repeat(5000), "나".repeat(5000))))
                .andExpect(status().isOk());
        assertThat(job.getCancelReason()).hasSize(5000);
        assertThat(job.getMessageToStudent()).hasSize(5000);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("본문이 없거나 두 입력 중 하나라도 누락·null·공백·5,001자이면 400 COMMON_400을 반환하고 취소 처리를 하지 않는다")
    void rejectsInvalidBody(String description, String body) throws Exception {
        MockHttpServletRequestBuilder request = post(URL).principal(authentication)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(body);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, ownerRepository, jobRepository, paymentRepository);
    }

    private static Stream<Arguments> invalidBodies() {
        String tooLong = "가".repeat(5001);
        return Stream.of(
                Arguments.of("본문 없음", null),
                Arguments.of("잘못된 JSON", "{\"cancelReason\":"),
                Arguments.of("빈 객체", "{}"),
                Arguments.of("취소 이유 누락", "{\"messageToStudent\":\"감사합니다.\"}"),
                Arguments.of("취소 이유 null", "{\"cancelReason\":null,\"messageToStudent\":\"감사합니다.\"}"),
                Arguments.of("취소 이유 빈 문자열", body("", MESSAGE_TO_STUDENT)),
                Arguments.of("취소 이유 공백", body(" \n\t ", MESSAGE_TO_STUDENT)),
                Arguments.of("취소 이유 5,001자", body(tooLong, MESSAGE_TO_STUDENT)),
                Arguments.of("남길 말 누락", "{\"cancelReason\":\"일정 변경\"}"),
                Arguments.of("남길 말 null", "{\"cancelReason\":\"일정 변경\",\"messageToStudent\":null}"),
                Arguments.of("남길 말 빈 문자열", body(CANCEL_REASON, "")),
                Arguments.of("남길 말 공백", body(CANCEL_REASON, " \n\t ")),
                Arguments.of("남길 말 5,001자", body(CANCEL_REASON, tooLong)));
    }

    @Test
    @DisplayName("이미 취소된 의뢰를 다시 취소하면 409 JOB_409_CANCEL을 반환하고 기존 취소 입력과 시각을 덮어쓰지 않는다")
    void rejectsAlreadyCancelledJobWithoutOverwriting() throws Exception {
        givenActiveOwner();
        LocalDateTime cancelledAt = LocalDateTime.of(2026, 9, 1, 10, 0);
        Job job = Job.builder().id(42L).ownerProfileId(5L).status(JobStatus.CANCELLED)
                .completedAt(cancelledAt).cancelReason("기존 이유").messageToStudent("기존 남길 말").build();
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(job));

        mockMvc.perform(cancelRequest(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_409_CANCEL"));
        assertThat(job.getCompletedAt()).isEqualTo(cancelledAt);
        assertThat(job.getCancelReason()).isEqualTo("기존 이유");
        assertThat(job.getMessageToStudent()).isEqualTo("기존 남길 말");
        verifyNoInteractions(paymentRepository, eventPublisher);
    }

    @Test
    @DisplayName("이미 완료된 의뢰를 취소하면 409 JOB_409_CANCEL을 반환하고 결제를 건드리지 않는다")
    void rejectsClosedJob() throws Exception {
        givenActiveOwner();
        Job job = givenOwnedJob(JobStatus.CLOSED);

        mockMvc.perform(cancelRequest(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_409_CANCEL"));
        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(job.getCancelReason()).isNull();
        assertThat(job.getMessageToStudent()).isNull();
        verifyNoInteractions(paymentRepository, eventPublisher);
    }

    @Test
    @DisplayName("다른 사업주의 의뢰이면 404 JOB_404를 반환한다")
    void rejectsOtherOwnersJob() throws Exception {
        givenActiveOwner();
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.empty());

        mockMvc.perform(cancelRequest(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(paymentRepository, eventPublisher);
    }

    @Test
    @DisplayName("사업주 프로필이 없는 사용자(학생 포함)는 403 OWNER_403으로 거부하고 의뢰를 조회하지 않는다")
    void rejectsUserWithoutOwnerProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.STUDENT).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(cancelRequest(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OWNER_403"));
        verifyNoInteractions(jobRepository, paymentRepository, eventPublisher);
    }

    @Test
    @DisplayName("의뢰 ID가 0 이하이면 400을 반환하고 조회하지 않는다")
    void rejectsNonPositiveJobId() throws Exception {
        mockMvc.perform(post("/jobs/0/cancel").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository, paymentRepository);
    }

    private MockHttpServletRequestBuilder cancelRequest(String body) {
        return post(URL).principal(authentication).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String body(String cancelReason, String messageToStudent) {
        return "{\"cancelReason\":\"" + escape(cancelReason) + "\",\"messageToStudent\":\""
                + escape(messageToStudent) + "\"}";
    }

    private static String escape(String value) {
        return value.replace("\n", "\\n").replace("\t", "\\t");
    }

    private void givenActiveOwner() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.of(
                Owner.builder().id(5L).userId(OWNER_USER_ID).storeName(STORE_NAME).build()));
    }

    private Job givenOwnedJob(JobStatus status) {
        Job job = Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .title(JOB_TITLE)
                .status(status)
                .selectedStudentProfileId(status == JobStatus.OPEN ? null : 7L)
                .build();
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(job));
        return job;
    }

    private Payment givenPaidPayment(Long amount) {
        Payment payment = Payment.pending(42L, 21L, OWNER_USER_ID, "order-123", amount, NOW);
        payment.recordKakaoTid("T1234567890123456789");
        payment.approve(NOW);
        ReflectionTestUtils.setField(payment, "id", 91L);
        when(paymentRepository.findByJobIdAndStatus(42L, PaymentStatus.PAID)).thenReturn(Optional.of(payment));
        return payment;
    }

    private JobApplication pendingApplication(Long id, Long studentProfileId) {
        return JobApplication.builder().id(id).jobId(42L).studentProfileId(studentProfileId)
                .status(JobApplicationStatus.PENDING).build();
    }

    private List<NotificationEvent> publishedEvents() {
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
    }
}
