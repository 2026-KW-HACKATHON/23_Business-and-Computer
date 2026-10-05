package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
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
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
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

@DisplayName("학생 의뢰 지원 전체 흐름 (POST /jobs/{jobId}/applications)")
class JobApplicationCreateFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final long JOB_ID = 42L;
    private static final long STUDENT_PROFILE_ID = 7L;
    private static final long APPLICATION_ID = 123L;
    private static final String URL = "/jobs/42/applications";
    private static final String SUMMARY = "매장 분위기에 맞는 메뉴판을 제작하겠습니다.";
    private static final String WORK_PLAN = "요구사항 확인 후 시안을 제작하고 피드백을 반영하겠습니다.";
    private static final String DELIVERY_METHOD = "인쇄용 PDF와 편집 가능한 원본 파일로 전달하겠습니다.";
    private static final String VALID_BODY = body(SUMMARY, WORK_PLAN, DELIVERY_METHOD, "true");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-29T03:15:30Z"), ZoneId.of("UTC"));
        UserService userService = new UserService(userRepository, mock(JwtService.class));
        JobService jobService = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
                jobApplicationRepository, mock(JobSubmissionRepository.class), clock);
        JobFacade facade = new JobFacade(userService, new OwnerService(mock(OwnerRepository.class)), jobService,
                mock(SpecialtyCategoryService.class), mock(SpecialtyService.class),
                new StudentService(studentRepository),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class),
                mock(PaymentService.class),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("모집 중 의뢰에 지원하면 로그인 학생의 프로필 ID로 PENDING 지원서를 저장하고 201과 지원서 ID를 반환한다")
    void createsPendingApplication() throws Exception {
        givenActiveStudent();
        Job job = givenJob(JobStatus.OPEN);
        givenSaveAssignsId();

        mockMvc.perform(applyRequest(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobApplicationId").value(APPLICATION_ID))
                .andExpect(jsonPath("$.data.length()").value(1));

        JobApplication saved = savedApplication();
        assertThat(saved.getStudentProfileId()).isEqualTo(STUDENT_PROFILE_ID);
        assertThat(saved.getJobId()).isEqualTo(JOB_ID);
        assertThat(saved.getSummary()).isEqualTo(SUMMARY);
        assertThat(saved.getWorkPlan()).isEqualTo(WORK_PLAN);
        assertThat(saved.getDeliveryMethod()).isEqualTo(DELIVERY_METHOD);
        assertThat(saved.getStatus()).isEqualTo(JobApplicationStatus.PENDING);
        verify(jobApplicationRepository).existsByJobIdAndStudentProfileId(JOB_ID, STUDENT_PROFILE_ID);
        // 지원만으로는 의뢰 상태와 선정 학생이 바뀌지 않는다
        assertThat(job.getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(job.getSelectedStudentProfileId()).isNull();
    }

    @Test
    @DisplayName("입력의 앞뒤 공백은 제거하고 내부 공백과 줄바꿈은 유지해 저장한다")
    void trimsInputsAndKeepsInnerLineBreaks() throws Exception {
        givenActiveStudent();
        givenJob(JobStatus.OPEN);
        givenSaveAssignsId();

        mockMvc.perform(applyRequest(body("  메뉴판  제작 ", "\n1. 시안\n2. 피드백 반영\t", " PDF\n원본 파일  ", "true")))
                .andExpect(status().isCreated());

        JobApplication saved = savedApplication();
        assertThat(saved.getSummary()).isEqualTo("메뉴판  제작");
        assertThat(saved.getWorkPlan()).isEqualTo("1. 시안\n2. 피드백 반영");
        assertThat(saved.getDeliveryMethod()).isEqualTo("PDF\n원본 파일");
    }

    @Test
    @DisplayName("한 줄 요약 255자, 작업계획·전달 방법 500자이면 지원을 허용한다")
    void acceptsMaxLengthInputs() throws Exception {
        givenActiveStudent();
        givenJob(JobStatus.OPEN);
        givenSaveAssignsId();

        mockMvc.perform(applyRequest(body("가".repeat(255), "나".repeat(500), "다".repeat(500), "true")))
                .andExpect(status().isCreated());

        JobApplication saved = savedApplication();
        assertThat(saved.getSummary()).hasSize(255);
        assertThat(saved.getWorkPlan()).hasSize(500);
        assertThat(saved.getDeliveryMethod()).hasSize(500);
    }

    @Test
    @DisplayName("작업 마감일이 지난 의뢰도 모집 중이면 지원할 수 있다")
    void allowsOpenJobPastDeadline() throws Exception {
        givenActiveStudent();
        when(jobRepository.findLockedById(JOB_ID)).thenReturn(Optional.of(Job.builder()
                .id(JOB_ID).ownerProfileId(5L).status(JobStatus.OPEN)
                .draftDeadline(LocalDate.of(2020, 1, 1)).finalDeadline(LocalDate.of(2020, 1, 5)).build()));
        givenSaveAssignsId();

        mockMvc.perform(applyRequest(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.jobApplicationId").value(APPLICATION_ID));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("본문이 없거나 문자열이 누락·null·공백·길이 초과이거나 동의가 누락·null·false이면 400 COMMON_400을 반환하고 아무것도 조회하지 않는다")
    void rejectsInvalidBody(String description, String body) throws Exception {
        MockHttpServletRequestBuilder request = post(URL).principal(authentication)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(body);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, studentRepository, jobRepository, jobApplicationRepository);
    }

    private static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("본문 없음", null),
                Arguments.of("잘못된 JSON", "{\"summary\":"),
                Arguments.of("빈 객체", "{}"),
                Arguments.of("한 줄 요약 누락", "{\"workPlan\":\"계획\",\"deliveryMethod\":\"전달\","
                        + "\"deadlineAndPenaltyAgreed\":true}"),
                Arguments.of("한 줄 요약 null", body(null, WORK_PLAN, DELIVERY_METHOD, "true")),
                Arguments.of("한 줄 요약 빈 문자열", body("", WORK_PLAN, DELIVERY_METHOD, "true")),
                Arguments.of("한 줄 요약 공백", body(" \n\t ", WORK_PLAN, DELIVERY_METHOD, "true")),
                Arguments.of("한 줄 요약 256자", body("가".repeat(256), WORK_PLAN, DELIVERY_METHOD, "true")),
                Arguments.of("작업계획 누락", "{\"summary\":\"요약\",\"deliveryMethod\":\"전달\","
                        + "\"deadlineAndPenaltyAgreed\":true}"),
                Arguments.of("작업계획 null", body(SUMMARY, null, DELIVERY_METHOD, "true")),
                Arguments.of("작업계획 빈 문자열", body(SUMMARY, "", DELIVERY_METHOD, "true")),
                Arguments.of("작업계획 공백", body(SUMMARY, " \n\t ", DELIVERY_METHOD, "true")),
                Arguments.of("작업계획 501자", body(SUMMARY, "나".repeat(501), DELIVERY_METHOD, "true")),
                Arguments.of("전달 방법 누락", "{\"summary\":\"요약\",\"workPlan\":\"계획\","
                        + "\"deadlineAndPenaltyAgreed\":true}"),
                Arguments.of("전달 방법 null", body(SUMMARY, WORK_PLAN, null, "true")),
                Arguments.of("전달 방법 빈 문자열", body(SUMMARY, WORK_PLAN, "", "true")),
                Arguments.of("전달 방법 공백", body(SUMMARY, WORK_PLAN, " \n\t ", "true")),
                Arguments.of("전달 방법 501자", body(SUMMARY, WORK_PLAN, "다".repeat(501), "true")),
                Arguments.of("동의 누락", "{\"summary\":\"요약\",\"workPlan\":\"계획\",\"deliveryMethod\":\"전달\"}"),
                Arguments.of("동의 null", body(SUMMARY, WORK_PLAN, DELIVERY_METHOD, "null")),
                Arguments.of("동의 false", body(SUMMARY, WORK_PLAN, DELIVERY_METHOD, "false")),
                Arguments.of("동의가 불리언이 아님", body(SUMMARY, WORK_PLAN, DELIVERY_METHOD, "\"동의\"")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-1", "abc" })
    @DisplayName("의뢰 ID가 양수가 아니거나 숫자가 아니면 400 COMMON_400을 반환하고 조회하지 않는다")
    void rejectsInvalidJobId(String jobId) throws Exception {
        mockMvc.perform(post("/jobs/" + jobId + "/applications").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, studentRepository, jobRepository, jobApplicationRepository);
    }

    @Test
    @DisplayName("잠겼거나 없는 사용자는 401 COMMON_401을 반환하고 의뢰를 조회하지 않는다")
    void rejectsLockedUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        mockMvc.perform(applyRequest(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(studentRepository, jobRepository, jobApplicationRepository);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = { "OWNER", "PENDING" })
    @DisplayName("사장님과 가입 대기 사용자는 403 JOB_APPLICATION_403_STUDENT로 거부하고 의뢰를 조회하지 않는다")
    void rejectsNonStudent(UserRole role) throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(role).build()));

        mockMvc.perform(applyRequest(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_403_STUDENT"));
        verifyNoInteractions(studentRepository, jobRepository, jobApplicationRepository);
    }

    @Test
    @DisplayName("학생 프로필이 없는 학생은 403 JOB_APPLICATION_403_STUDENT로 거부하고 의뢰를 조회하지 않는다")
    void rejectsStudentWithoutProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(applyRequest(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_403_STUDENT"));
        verifyNoInteractions(jobRepository, jobApplicationRepository);
    }

    @Test
    @DisplayName("없는 의뢰이면 404 JOB_404를 반환하고 지원서를 저장하지 않는다")
    void rejectsMissingJob() throws Exception {
        givenActiveStudent();
        when(jobRepository.findLockedById(JOB_ID)).thenReturn(Optional.empty());

        mockMvc.perform(applyRequest(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobApplicationRepository);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = "OPEN", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("모집 중이 아닌 의뢰(MATCHED·CLOSED·CANCELLED)는 409 JOB_APPLICATION_409_STATUS로 거부하고 지원서를 저장하지 않는다")
    void rejectsJobThatIsNotOpen(JobStatus status) throws Exception {
        givenActiveStudent();
        Job job = givenJob(status);

        mockMvc.perform(applyRequest(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_409_STATUS"));
        verifyNoInteractions(jobApplicationRepository);
        assertThat(job.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("이미 지원한 의뢰이면 409 JOB_APPLICATION_409_DUPLICATE를 반환하고 지원서를 저장하지 않는다")
    void rejectsDuplicateApplication() throws Exception {
        givenActiveStudent();
        givenJob(JobStatus.OPEN);
        when(jobApplicationRepository.existsByJobIdAndStudentProfileId(JOB_ID, STUDENT_PROFILE_ID)).thenReturn(true);

        mockMvc.perform(applyRequest(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_409_DUPLICATE"));
        verify(jobApplicationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("저장 중 지원서 유니크 제약이 충돌하면 409 JOB_APPLICATION_409_DUPLICATE로 변환한다")
    void convertsUniqueConstraintViolationToDuplicate() throws Exception {
        givenActiveStudent();
        givenJob(JobStatus.OPEN);
        when(jobApplicationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"job_applications_job_id_student_profile_id_key\""));

        mockMvc.perform(applyRequest(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_409_DUPLICATE"));
    }

    @Test
    @DisplayName("저장 중 다른 무결성 오류는 중복 지원으로 바꾸지 않고 기존 409 COMMON_409 처리에 맡긴다")
    void leavesOtherIntegrityViolations() throws Exception {
        givenActiveStudent();
        givenJob(JobStatus.OPEN);
        when(jobApplicationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "value too long for type character varying(500)"));

        mockMvc.perform(applyRequest(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COMMON_409"));
    }

    private MockHttpServletRequestBuilder applyRequest(String body) {
        return post(URL).principal(authentication).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** 문자열이 null이면 JSON null로, 동의 값은 넘긴 JSON 리터럴 그대로 넣는다. */
    private static String body(String summary, String workPlan, String deliveryMethod, String agreedLiteral) {
        return "{\"summary\":" + quote(summary) + ",\"workPlan\":" + quote(workPlan)
                + ",\"deliveryMethod\":" + quote(deliveryMethod)
                + ",\"deadlineAndPenaltyAgreed\":" + agreedLiteral + "}";
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value.replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }

    private void givenActiveStudent() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(
                Student.builder().id(STUDENT_PROFILE_ID).userId(STUDENT_USER_ID).build()));
    }

    private Job givenJob(JobStatus status) {
        Job job = Job.builder()
                .id(JOB_ID)
                .ownerProfileId(5L)
                .status(status)
                .selectedStudentProfileId(status == JobStatus.OPEN || status == JobStatus.CANCELLED ? null : 9L)
                .build();
        when(jobRepository.findLockedById(JOB_ID)).thenReturn(Optional.of(job));
        return job;
    }

    private void givenSaveAssignsId() {
        when(jobApplicationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            JobApplication application = invocation.getArgument(0);
            return JobApplication.builder()
                    .id(APPLICATION_ID)
                    .studentProfileId(application.getStudentProfileId())
                    .jobId(application.getJobId())
                    .summary(application.getSummary())
                    .workPlan(application.getWorkPlan())
                    .deliveryMethod(application.getDeliveryMethod())
                    .status(application.getStatus())
                    .build();
        });
    }

    private JobApplication savedApplication() {
        ArgumentCaptor<JobApplication> captor = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest(name = "학생 {0}, 의뢰 {1}")
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "null, 01K6DEMO00000000000000000A",
            "01K6DEMO00000000000000000A, null",
            "01K6DEMO00000000000000000A, 01K6DEMO00000000000000000B"}, nullValues = "null")
    @DisplayName("학생과 격리 범위가 다른 의뢰에는 없는 의뢰와 같은 404 JOB_404를 반환하고 지원서를 저장하지 않는다")
    void rejectsJobOutsideStudentDemoSession(String studentSession, String jobSession) throws Exception {
        givenStudentInDemoSession(studentSession);
        when(jobRepository.findLockedById(JOB_ID)).thenReturn(Optional.of(Job.builder()
                .id(JOB_ID).ownerProfileId(5L).status(JobStatus.OPEN).demoSessionId(jobSession).build()));

        mockMvc.perform(applyRequest(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobApplicationRepository);
    }

    @Test
    @DisplayName("데모 학생은 같은 데모 세션의 모집 중 의뢰에 지원할 수 있다")
    void createsApplicationWithinDemoSession() throws Exception {
        givenStudentInDemoSession("01K6DEMO00000000000000000A");
        when(jobRepository.findLockedById(JOB_ID)).thenReturn(Optional.of(Job.builder()
                .id(JOB_ID).ownerProfileId(5L).status(JobStatus.OPEN)
                .demoSessionId("01K6DEMO00000000000000000A").build()));
        givenSaveAssignsId();

        mockMvc.perform(applyRequest(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
        verify(jobApplicationRepository).saveAndFlush(any());
    }

    private void givenStudentInDemoSession(String demoSessionId) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).demoSessionId(demoSessionId).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(
                Student.builder().id(STUDENT_PROFILE_ID).userId(STUDENT_USER_ID).build()));
    }
}
