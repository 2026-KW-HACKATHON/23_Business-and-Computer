package com.gakkum.backend.application.job;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
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

@DisplayName("완료 결과물 조회 전체 흐름 (GET /jobs/{jobId}/result)")
class JobResultFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final String URL = "/jobs/42/result";
    private static final Instant PAID_AT = Instant.parse("2026-09-01T12:00:00Z");
    private static final String STARTED_DATE = LocalDate.ofInstant(PAID_AT, ZoneId.systemDefault()).toString();

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, mock(JwtService.class));
        JobService jobService = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
                mock(JobApplicationRepository.class), jobSubmissionRepository, Clock.systemUTC());
        JobFacade facade = new JobFacade(userService, new OwnerService(ownerRepository), jobService,
                mock(SpecialtyCategoryService.class), mock(SpecialtyService.class), new StudentService(studentRepository),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class),
                new PaymentService(paymentRepository, Clock.systemUTC()),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("사장님이 초안만 승인한 의뢰를 조회하면 초안 파일·메시지와 시작·초안 제출·완료 이력을 반환한다")
    void ownerGetsDraftOnlyResult() throws Exception {
        givenActiveOwner(5L);
        givenClosedJob();
        givenSubmissions(submission(81L, 0, JobSubmissionReviewStatus.APPROVED,
                LocalDateTime.of(2026, 9, 10, 9, 30), null));
        givenPaidPayment();

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(42))
                .andExpect(jsonPath("$.data.title").value("가게 메뉴판 디자인"))
                .andExpect(jsonPath("$.data.studentName").value("김학생"))
                .andExpect(jsonPath("$.data.completedAt").value("2026-09-21"))
                .andExpect(jsonPath("$.data.normalCompleted").value(true))
                .andExpect(jsonPath("$.data.workFee").value(150000))
                .andExpect(jsonPath("$.data.fileUrls", contains("https://cdn.example/81-a.png", "https://cdn.example/81-b.pdf")))
                .andExpect(jsonPath("$.data.message").value("제출물 81"))
                .andExpect(jsonPath("$.data.workHistory[*].type",
                        contains("STARTED", "DRAFT_SUBMITTED", "COMPLETED")))
                .andExpect(jsonPath("$.data.workHistory[*].date",
                        contains(STARTED_DATE, "2026-09-10", "2026-09-21")));
    }

    @Test
    @DisplayName("제안으로 만든 의뢰의 시작 이력은 결제 승인일이 아니라 학생이 실제로 작업을 시작한 날이다")
    void proposalJobUsesActualStartDate() throws Exception {
        givenActiveOwner(5L);
        givenClosedJob();
        when(jobRepository.findById(42L)).thenReturn(Optional.of(Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .title("가게 메뉴판 디자인")
                .budget(150000L)
                .status(JobStatus.CLOSED)
                .selectedStudentProfileId(7L)
                .proposalId(31L)
                .startedAt(LocalDateTime.of(2026, 9, 5, 10, 0))
                .completedAt(LocalDateTime.of(2026, 9, 20, 15, 0))
                .build()));
        givenSubmissions(submission(81L, 0, JobSubmissionReviewStatus.APPROVED,
                LocalDateTime.of(2026, 9, 10, 9, 30), null));
        givenPaidPayment();

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workHistory[*].type",
                        contains("STARTED", "DRAFT_SUBMITTED", "COMPLETED")))
                .andExpect(jsonPath("$.data.workHistory[*].date",
                        contains("2026-09-05", "2026-09-10", "2026-09-21")));
    }

    @ParameterizedTest
    @DisplayName("완료 날짜와 완료 이력 날짜는 UTC로 저장된 완료 시각의 한국 날짜로 일치하고, 제출 날짜는 저장된 날짜 그대로다")
    @CsvSource({
            "2026-10-06T14:59:59, 2026-10-06",
            "2026-10-06T15:00:00, 2026-10-07",
            "2026-10-06T23:59:59, 2026-10-07",
            "2026-10-31T15:00:00, 2026-11-01",
            "2026-12-31T15:00:00, 2027-01-01"
    })
    void returnsKoreanCompletedDate(String completedAtUtc, String expectedDate) throws Exception {
        givenActiveOwner(5L);
        givenClosedJob();
        givenClosedJobCompletedAt(null, null, completedAtUtc);
        givenSubmissions(submission(81L, 0, JobSubmissionReviewStatus.APPROVED,
                LocalDateTime.of(2026, 9, 10, 23, 30), null));
        givenPaidPayment();

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedAt").value(expectedDate))
                .andExpect(jsonPath("$.data.workHistory[*].date",
                        contains(STARTED_DATE, "2026-09-10", expectedDate)));
    }

    @ParameterizedTest
    @DisplayName("제안으로 만든 의뢰도 완료 날짜는 한국 날짜로 내리고 시작 날짜는 저장된 날짜 그대로다")
    @CsvSource({
            "2026-10-06T14:59:59, 2026-10-06",
            "2026-10-06T15:00:00, 2026-10-07",
            "2026-10-06T23:59:59, 2026-10-07",
            "2026-10-31T15:00:00, 2026-11-01",
            "2026-12-31T15:00:00, 2027-01-01"
    })
    void returnsKoreanCompletedDateForProposalJob(String completedAtUtc, String expectedDate) throws Exception {
        givenActiveOwner(5L);
        givenClosedJob();
        givenClosedJobCompletedAt(31L, LocalDateTime.of(2026, 9, 5, 23, 0), completedAtUtc);
        givenSubmissions(submission(81L, 0, JobSubmissionReviewStatus.APPROVED,
                LocalDateTime.of(2026, 9, 10, 9, 30), null));
        givenPaidPayment();

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedAt").value(expectedDate))
                .andExpect(jsonPath("$.data.workHistory[*].date",
                        contains("2026-09-05", "2026-09-10", expectedDate)));
    }

    @Test
    @DisplayName("담당 학생이 수정 후 승인된 의뢰를 조회하면 최종 수정안의 파일·메시지와 수정 요청을 포함한 이력을 반환한다")
    void studentGetsRevisedResult() throws Exception {
        givenActiveStudent(7L);
        givenClosedJob();
        givenSubmissions(
                submission(81L, 0, JobSubmissionReviewStatus.REVISION_REQUESTED,
                        LocalDateTime.of(2026, 9, 10, 9, 30), LocalDateTime.of(2026, 9, 11, 18, 0)),
                submission(82L, 1, JobSubmissionReviewStatus.APPROVED,
                        LocalDateTime.of(2026, 9, 13, 10, 0), null));
        givenPaidPayment();

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileUrls", contains("https://cdn.example/82-a.png", "https://cdn.example/82-b.pdf")))
                .andExpect(jsonPath("$.data.message").value("제출물 82"))
                .andExpect(jsonPath("$.data.workHistory[*].type", contains(
                        "STARTED", "DRAFT_SUBMITTED", "REVISION_REQUESTED", "REVISION_SUBMITTED", "COMPLETED")))
                .andExpect(jsonPath("$.data.workHistory[*].date", contains(
                        STARTED_DATE, "2026-09-10", "2026-09-11", "2026-09-13", "2026-09-21")));
    }

    @Test
    @DisplayName("여러 번 수정한 의뢰는 수정 요청을 각각 표시하고, 요청 시각이 없는 과거 수정 요청은 date를 null로 반환한다")
    void listsEveryRevisionRequestWithNullForLegacy() throws Exception {
        givenActiveOwner(5L);
        givenClosedJob();
        givenSubmissions(
                submission(81L, 0, JobSubmissionReviewStatus.REVISION_REQUESTED,
                        LocalDateTime.of(2026, 9, 10, 9, 30), null),
                submission(82L, 1, JobSubmissionReviewStatus.REVISION_REQUESTED,
                        LocalDateTime.of(2026, 9, 12, 9, 30), LocalDateTime.of(2026, 9, 14, 23, 59)),
                submission(83L, 2, JobSubmissionReviewStatus.APPROVED,
                        LocalDateTime.of(2026, 9, 16, 0, 1), null));
        givenPaidPayment();

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("제출물 83"))
                .andExpect(jsonPath("$.data.workHistory[*].type", contains(
                        "STARTED", "DRAFT_SUBMITTED", "REVISION_REQUESTED", "REVISION_SUBMITTED",
                        "REVISION_REQUESTED", "REVISION_SUBMITTED", "COMPLETED")))
                .andExpect(jsonPath("$.data.workHistory[2]", hasKey("date")))
                .andExpect(jsonPath("$.data.workHistory[2].date").value(nullValue()))
                .andExpect(jsonPath("$.data.workHistory[4].date").value("2026-09-14"))
                .andExpect(jsonPath("$.data.workHistory[5].date").value("2026-09-16"));
    }

    @Test
    @DisplayName("다른 사장님이 조회하면 404 JOB_RESULT_404를 반환한다")
    void rejectsOtherOwner() throws Exception {
        givenActiveOwner(6L);
        givenClosedJob();

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_RESULT_404"));
        verifyNoInteractions(jobSubmissionRepository, paymentRepository);
    }

    @Test
    @DisplayName("담당하지 않은 학생이 조회하면 404 JOB_RESULT_404를 반환한다")
    void rejectsOtherStudent() throws Exception {
        givenActiveStudent(8L);
        givenClosedJob();

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_RESULT_404"));
        verifyNoInteractions(jobSubmissionRepository, paymentRepository);
    }

    @Test
    @DisplayName("학생 프로필이 없는 학생이나 가입 대기 사용자는 404 JOB_RESULT_404를 반환하고 의뢰를 조회하지 않는다")
    void rejectsUserWithoutViewerProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.empty());
        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_RESULT_404"));

        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.PENDING).build()));
        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_RESULT_404"));
        verifyNoInteractions(jobRepository);
    }

    @Test
    @DisplayName("아직 완료되지 않은 의뢰는 사장님이 조회해도 404 JOB_RESULT_404를 반환한다")
    void rejectsJobNotClosed() throws Exception {
        givenActiveOwner(5L);
        when(jobRepository.findById(42L)).thenReturn(Optional.of(Job.builder()
                .id(42L).ownerProfileId(5L).selectedStudentProfileId(7L).status(JobStatus.MATCHED).build()));

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_RESULT_404"));
    }

    @Test
    @DisplayName("존재하지 않는 의뢰는 404 JOB_RESULT_404를 반환한다")
    void rejectsMissingJob() throws Exception {
        givenActiveOwner(5L);
        when(jobRepository.findById(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_RESULT_404"));
    }

    @Test
    @DisplayName("완료된 의뢰에 결제 완료 주문이 없으면 데이터 오류로 500을 반환한다")
    void rejectsClosedJobWithoutPaidPayment() throws Exception {
        givenActiveOwner(5L);
        givenClosedJob();
        givenSubmissions(submission(81L, 0, JobSubmissionReviewStatus.APPROVED,
                LocalDateTime.of(2026, 9, 10, 9, 30), null));
        when(paymentRepository.findByJobIdAndStatus(42L, PaymentStatus.PAID)).thenReturn(Optional.empty());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("의뢰 ID가 0 이하이면 400을 반환하고 조회하지 않는다")
    void rejectsNonPositiveJobId() throws Exception {
        mockMvc.perform(get("/jobs/0/result").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository);
    }

    private void givenActiveOwner(Long ownerProfileId) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID))
                .thenReturn(Optional.of(Owner.builder().id(ownerProfileId).build()));
    }

    private void givenActiveStudent(Long studentProfileId) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID))
                .thenReturn(Optional.of(Student.builder().id(studentProfileId).userId(STUDENT_USER_ID).build()));
    }

    private void givenClosedJob() {
        when(jobRepository.findById(42L)).thenReturn(Optional.of(Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .title("가게 메뉴판 디자인")
                .budget(150000L)
                .status(JobStatus.CLOSED)
                .selectedStudentProfileId(7L)
                // UTC 15시는 한국 시간으로 다음 날 0시다
                .completedAt(LocalDateTime.of(2026, 9, 20, 15, 0))
                .build()));
        when(studentRepository.findById(7L))
                .thenReturn(Optional.of(Student.builder().id(7L).userId(STUDENT_USER_ID).build()));
        when(userRepository.findById(STUDENT_USER_ID))
                .thenReturn(Optional.of(User.builder().id(STUDENT_USER_ID).name("김학생").build()));
    }

    private void givenClosedJobCompletedAt(Long proposalId, LocalDateTime startedAt, String completedAtUtc) {
        when(jobRepository.findById(42L)).thenReturn(Optional.of(Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .title("가게 메뉴판 디자인")
                .budget(150000L)
                .status(JobStatus.CLOSED)
                .selectedStudentProfileId(7L)
                .proposalId(proposalId)
                .startedAt(startedAt)
                .completedAt(LocalDateTime.parse(completedAtUtc))
                .build()));
    }

    private void givenSubmissions(JobSubmission... submissions) {
        when(jobSubmissionRepository.findByJobIdOrderByRevisionNumberAsc(42L)).thenReturn(List.of(submissions));
    }

    private void givenPaidPayment() {
        Payment payment = Payment.pending(42L, 11L, OWNER_USER_ID, "order-1", 150000L, PAID_AT);
        payment.recordKakaoTid("T1234567890");
        payment.approve(PAID_AT);
        when(paymentRepository.findByJobIdAndStatus(42L, PaymentStatus.PAID)).thenReturn(Optional.of(payment));
    }

    private JobSubmission submission(Long id, int revisionNumber, JobSubmissionReviewStatus reviewStatus,
            LocalDateTime createdAt, LocalDateTime reviewedAt) {
        return JobSubmission.builder()
                .id(id)
                .jobId(42L)
                .submissionType(revisionNumber == 0 ? JobSubmissionType.DRAFT : JobSubmissionType.REVISION)
                .revisionNumber(revisionNumber)
                .fileUrls(List.of("https://cdn.example/" + id + "-a.png", "https://cdn.example/" + id + "-b.pdf"))
                .message("제출물 " + id)
                .reviewStatus(reviewStatus)
                .reviewedAt(reviewedAt)
                .createdAt(createdAt)
                .build();
    }
}
