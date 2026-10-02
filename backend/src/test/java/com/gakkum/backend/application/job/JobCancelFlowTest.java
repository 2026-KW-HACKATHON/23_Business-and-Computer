package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("사장님 의뢰 취소 전체 흐름 (POST /jobs/{jobId}/cancel)")
class JobCancelFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final Instant NOW = Instant.parse("2026-09-29T03:15:30Z");
    private static final String URL = "/jobs/42/cancel";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        UserService userService = new UserService(userRepository, mock(JwtService.class));
        JobService jobService = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
                mock(JobApplicationRepository.class), mock(JobSubmissionRepository.class), clock);
        JobFacade facade = new JobFacade(userService, new OwnerService(ownerRepository), jobService,
                mock(SpecialtyCategoryService.class), mock(SpecialtyService.class), new StudentService(mock(StudentRepository.class)),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class),
                new PaymentService(paymentRepository, clock));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("진행 중 의뢰를 취소하면 의뢰는 CANCELLED, 결제는 REFUNDED가 되고 학생 보상금 20%를 뺀 금액을 환불 금액으로 반환한다")
    void cancelsMatchedJobAndRefunds() throws Exception {
        givenActiveOwner();
        Job job = givenOwnedJob(JobStatus.MATCHED);
        Payment payment = givenPaidPayment(100_000L);

        mockMvc.perform(post(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(42))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paidAmount").value(100_000))
                .andExpect(jsonPath("$.data.studentCompensationAmount").value(20_000))
                .andExpect(jsonPath("$.data.refundAmount").value(80_000))
                .andExpect(jsonPath("$.data.cancelledAt").exists());
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.getCompletedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneId.systemDefault()));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("모집 중 의뢰를 취소하면 결제 없이 CANCELLED가 되고 금액은 모두 0이다")
    void cancelsOpenJobWithoutRefund() throws Exception {
        givenActiveOwner();
        Job job = givenOwnedJob(JobStatus.OPEN);

        mockMvc.perform(post(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paidAmount").value(0))
                .andExpect(jsonPath("$.data.studentCompensationAmount").value(0))
                .andExpect(jsonPath("$.data.refundAmount").value(0));
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("이미 완료된 의뢰를 취소하면 409 JOB_409_CANCEL을 반환하고 결제를 건드리지 않는다")
    void rejectsClosedJob() throws Exception {
        givenActiveOwner();
        Job job = givenOwnedJob(JobStatus.CLOSED);

        mockMvc.perform(post(URL).principal(authentication))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_409_CANCEL"));
        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("다른 사업주의 의뢰이면 404 JOB_404를 반환한다")
    void rejectsOtherOwnersJob() throws Exception {
        givenActiveOwner();
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.empty());

        mockMvc.perform(post(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(paymentRepository);
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
        verifyNoInteractions(jobRepository, paymentRepository);
    }

    @Test
    @DisplayName("의뢰 ID가 0 이하이면 400을 반환하고 조회하지 않는다")
    void rejectsNonPositiveJobId() throws Exception {
        mockMvc.perform(post("/jobs/0/cancel").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository, paymentRepository);
    }

    private void givenActiveOwner() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.of(Owner.builder().id(5L).build()));
    }

    private Job givenOwnedJob(JobStatus status) {
        Job job = Job.builder()
                .id(42L)
                .ownerProfileId(5L)
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
        when(paymentRepository.findByJobIdAndStatus(42L, PaymentStatus.PAID)).thenReturn(Optional.of(payment));
        return payment;
    }
}
