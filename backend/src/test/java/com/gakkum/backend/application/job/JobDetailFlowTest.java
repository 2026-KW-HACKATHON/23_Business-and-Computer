package com.gakkum.backend.application.job;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
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
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.repository.StudentSpecialtyRepository;
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

@DisplayName("의뢰 상세 조회 전체 흐름 (GET /jobs/{jobId})")
class JobDetailFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final Long JOB_OWNER_PROFILE_ID = 999L;
    private static final Long SELECTED_STUDENT_PROFILE_ID = 7L;
    private static final String STORE_NAME = "가꿈 베이커리";
    private static final String STORE_ADDRESS = "서울특별시 노원구 광운로 20";
    private static final String CANCEL_REASON = "가게 운영 계획이 변경되었습니다.";
    private static final String MESSAGE_TO_STUDENT = "함께하지 못해 아쉽습니다.";
    private static final LocalDateTime CANCELLED_AT = LocalDateTime.of(2026, 10, 4, 12, 0);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final SpecialtyCategoryRepository specialtyCategoryRepository = mock(SpecialtyCategoryRepository.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, mock(JwtService.class));
        SpecialtyService specialtyService = new SpecialtyService(specialtyRepository,
                mock(StudentSpecialtyRepository.class));
        JobService jobService = new JobService(jobRepository, jobSpecialtyRepository,
                jobApplicationRepository, mock(JobSubmissionRepository.class), Clock.systemUTC());
        SpecialtyCategoryService specialtyCategoryService =
                new SpecialtyCategoryService(specialtyCategoryRepository, specialtyRepository);
        JobFacade facade = new JobFacade(userService, new OwnerService(ownerRepository), jobService,
                specialtyCategoryService, specialtyService, new StudentService(studentRepository),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class),
                new PaymentService(paymentRepository, Clock.systemUTC()),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class), mock(ApplicationEventPublisher.class),
                new ImmediateTransactionTemplate(), mock(ChatRoomService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        // 매장 정보는 모든 상세 조회가 읽으므로 의뢰한 사장님 프로필을 기본으로 둔다
        givenJobOwnerStore(STORE_ADDRESS);
    }

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    @DisplayName("작성자가 아닌 학생도 모든 상태의 의뢰 상세 필드와 매장명·주소를 조회한다")
    void returnsJobDetailForEveryStatus(JobStatus jobStatus) throws Exception {
        givenActiveStudent();
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job(jobStatus)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of(
                JobSpecialty.create(42L, 21L),
                JobSpecialty.create(42L, 12L),
                JobSpecialty.create(42L, 11L)));
        when(specialtyRepository.findAllById(any())).thenReturn(List.of(
                specialty(21L, 2L, "웹 디자인"),
                specialty(12L, 1L, "프론트엔드"),
                specialty(11L, 1L, "백엔드")));
        when(specialtyCategoryRepository.findAllById(any())).thenReturn(List.of(
                category(2L, "디자인"), category(1L, "개발")));

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.jobId").doesNotExist())
                .andExpect(jsonPath("$.data.ownerProfileId").doesNotExist())
                .andExpect(jsonPath("$.data.status").value(jobStatus.name()))
                .andExpect(jsonPath("$.data.title").value("가게 홍보 웹사이트 제작"))
                .andExpect(jsonPath("$.data.description").value("메뉴와 위치를 소개하는 웹사이트가 필요합니다."))
                .andExpect(jsonPath("$.data.budget").value(300000))
                .andExpect(jsonPath("$.data.specialtyCategories.length()").value(2))
                .andExpect(jsonPath("$.data.specialtyCategories[0].id").value(1))
                .andExpect(jsonPath("$.data.specialtyCategories[0].name").value("개발"))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[0].id").value(11))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[0].name").value("백엔드"))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[1].id").value(12))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[1].name").value("프론트엔드"))
                .andExpect(jsonPath("$.data.specialtyCategories[1].id").value(2))
                .andExpect(jsonPath("$.data.specialtyCategories[1].name").value("디자인"))
                .andExpect(jsonPath("$.data.specialtyCategories[1].specialties[0].id").value(21))
                .andExpect(jsonPath("$.data.specialtyCategories[1].specialties[0].name").value("웹 디자인"))
                .andExpect(jsonPath("$.data.draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.finalDeadline").value("2026-10-20"))
                .andExpect(jsonPath("$.data.revisionCount").value(1))
                .andExpect(jsonPath("$.data.storeName").value(STORE_NAME))
                .andExpect(jsonPath("$.data.storeAddress").value(STORE_ADDRESS))
                .andExpect(jsonPath("$.data", not(hasKey("applied"))));
        expectNoCancellationInfo(mockMvc.perform(get("/jobs/42").principal(authentication)));
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("결제 후 취소된 의뢰를 의뢰한 사장님이 조회하면 취소 정보와 저장된 환불액·학생 정산액을 반환한다")
    void returnsCancellationInfoToOwner() throws Exception {
        givenActiveUser(UserRole.OWNER);
        givenOwnerProfileOfUser(JOB_OWNER_PROFILE_ID);
        givenJob(cancelledJob(SELECTED_STUDENT_PROFILE_ID));
        givenRefundedPayment(70_000L, 30_000L);

        expectPaidCancellationInfo(mockMvc.perform(get("/jobs/42").principal(authentication)));
    }

    @Test
    @DisplayName("결제 후 취소된 의뢰를 선정 학생이 조회하면 취소 정보와 저장된 환불액·학생 정산액을 반환한다")
    void returnsCancellationInfoToSelectedStudent() throws Exception {
        givenActiveUser(UserRole.STUDENT);
        givenStudentProfileOfUser(SELECTED_STUDENT_PROFILE_ID);
        givenJob(cancelledJob(SELECTED_STUDENT_PROFILE_ID));
        givenRefundedPayment(70_000L, 30_000L);
        givenApplication(SELECTED_STUDENT_PROFILE_ID, JobApplicationStatus.ACCEPTED);

        expectPaidCancellationInfo(mockMvc.perform(get("/jobs/42").principal(authentication)))
                .andExpect(jsonPath("$.data.applied").value("ACCEPTED"));
        // 지원 상태와 취소 당사자 판정이 학생 프로필 조회 한 번을 함께 쓴다
        verify(studentRepository).findByUserId(USER_ID);
    }

    @Test
    @DisplayName("결제 전 취소된 의뢰는 결제를 조회하지 않고 환불액과 학생 정산액을 0으로 반환한다")
    void returnsZeroAmountsForCancellationBeforePayment() throws Exception {
        givenActiveUser(UserRole.OWNER);
        givenOwnerProfileOfUser(JOB_OWNER_PROFILE_ID);
        givenJob(cancelledJob(null));

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.storeName").value(STORE_NAME))
                .andExpect(jsonPath("$.data.cancelledBy").value("OWNER"))
                .andExpect(jsonPath("$.data.cancelReason").value(CANCEL_REASON))
                .andExpect(jsonPath("$.data.refundAmount").value(0))
                .andExpect(jsonPath("$.data.studentCompensationAmount").value(0));
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("취소 이유·남긴 말·취소 시각이 없는 과거 취소 기록은 해당 필드를 null로 반환한다")
    void returnsNullForLegacyCancellationRecord() throws Exception {
        givenActiveUser(UserRole.OWNER);
        givenOwnerProfileOfUser(JOB_OWNER_PROFILE_ID);
        givenJob(jobBuilder(JobStatus.CANCELLED).build());

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value(STORE_NAME))
                .andExpect(jsonPath("$.data.cancelledBy").value("OWNER"))
                .andExpect(jsonPath("$.data.cancelReason").value(nullValue()))
                .andExpect(jsonPath("$.data.messageToStudent").value(nullValue()))
                .andExpect(jsonPath("$.data.cancelledAt").value(nullValue()))
                .andExpect(jsonPath("$.data.refundAmount").value(0))
                .andExpect(jsonPath("$.data.studentCompensationAmount").value(0));
    }

    @Test
    @DisplayName("다른 사장님에게는 취소 정보를 노출하지 않고 결제도 조회하지 않는다")
    void hidesCancellationInfoFromOtherOwner() throws Exception {
        givenActiveUser(UserRole.OWNER);
        givenOwnerProfileOfUser(5L);
        givenJob(cancelledJob(SELECTED_STUDENT_PROFILE_ID));

        expectHiddenCancellationInfo();
    }

    @Test
    @DisplayName("선정되지 않은 다른 학생에게는 취소 정보를 노출하지 않고 결제도 조회하지 않는다")
    void hidesCancellationInfoFromOtherStudent() throws Exception {
        givenActiveUser(UserRole.STUDENT);
        givenStudentProfileOfUser(8L);
        givenJob(cancelledJob(SELECTED_STUDENT_PROFILE_ID));

        expectHiddenCancellationInfo();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"OWNER", "STUDENT"})
    @DisplayName("역할에 맞는 프로필이 없는 사용자에게는 취소 정보를 노출하지 않고 결제도 조회하지 않는다")
    void hidesCancellationInfoFromUserWithoutProfile(UserRole role) throws Exception {
        givenActiveUser(role);
        givenJob(cancelledJob(SELECTED_STUDENT_PROFILE_ID));

        expectHiddenCancellationInfo();
    }

    @Test
    @DisplayName("모집 중 취소된 의뢰는 선정 학생이 없으므로 학생에게 취소 정보를 노출하지 않는다")
    void hidesCancellationInfoOfUnmatchedJobFromStudent() throws Exception {
        givenActiveUser(UserRole.STUDENT);
        givenJob(cancelledJob(null));

        expectHiddenCancellationInfo();
    }

    @Test
    @DisplayName("취소되지 않은 의뢰는 의뢰한 사장님에게도 취소 정보를 null로 반환한다")
    void returnsNoCancellationInfoForActiveJob() throws Exception {
        givenActiveUser(UserRole.OWNER);
        givenOwnerProfileOfUser(JOB_OWNER_PROFILE_ID);
        givenJob(jobBuilder(JobStatus.MATCHED).selectedStudentProfileId(SELECTED_STUDENT_PROFILE_ID).build());

        expectNoCancellationInfo(mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATCHED"))
                .andExpect(jsonPath("$.data.storeName").value(STORE_NAME)));
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("결제 후 취소된 의뢰에 환불 기록이 없으면 500을 반환한다")
    void rejectsPaidCancellationWithoutRefundedPayment() throws Exception {
        givenActiveUser(UserRole.OWNER);
        givenOwnerProfileOfUser(JOB_OWNER_PROFILE_ID);
        givenJob(cancelledJob(SELECTED_STUDENT_PROFILE_ID));
        when(paymentRepository.findByJobIdAndStatus(42L, PaymentStatus.REFUNDED)).thenReturn(Optional.empty());

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("환불 기록에 환불액 또는 학생 정산액이 없으면 500을 반환한다")
    void rejectsRefundedPaymentWithoutStoredAmount(boolean missingRefundAmount) throws Exception {
        givenActiveUser(UserRole.STUDENT);
        givenStudentProfileOfUser(SELECTED_STUDENT_PROFILE_ID);
        givenJob(cancelledJob(SELECTED_STUDENT_PROFILE_ID));
        givenRefundedPayment(missingRefundAmount ? null : 70_000L, missingRefundAmount ? 30_000L : null);

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @ParameterizedTest
    @EnumSource(JobApplicationStatus.class)
    @DisplayName("학생에게는 본인 지원서 상태를 Boolean이 아닌 문자열 그대로 반환한다")
    void returnsOwnApplicationStatus(JobApplicationStatus applicationStatus) throws Exception {
        givenActiveUser(UserRole.STUDENT);
        givenStudentProfileOfUser(8L);
        givenJob(job(JobStatus.OPEN));
        givenApplication(8L, applicationStatus);

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied").isString())
                .andExpect(jsonPath("$.data.applied").value(applicationStatus.name()))
                // 공고 상태와 본인 지원 상태는 별개로 내린다
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    @DisplayName("다른 학생만 지원한 의뢰는 본인 지원 이력이 없으므로 applied 키를 내리지 않는다")
    void omitsAppliedWhenOnlyOtherStudentApplied() throws Exception {
        givenActiveUser(UserRole.STUDENT);
        givenStudentProfileOfUser(8L);
        givenJob(job(JobStatus.OPEN));
        givenApplication(SELECTED_STUDENT_PROFILE_ID, JobApplicationStatus.PENDING);

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data", not(hasKey("applied"))));
        verify(jobApplicationRepository).findByStudentProfileIdAndJobIdIn(8L, List.of(42L));
    }

    @Test
    @DisplayName("학생 프로필이 없는 학생에게는 지원서를 조회하지 않고 applied 키를 내리지 않는다")
    void omitsAppliedForStudentWithoutProfile() throws Exception {
        givenActiveUser(UserRole.STUDENT);
        givenJob(job(JobStatus.OPEN));

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data", not(hasKey("applied"))));
        verifyNoInteractions(jobApplicationRepository);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, mode = EnumSource.Mode.EXCLUDE, names = "STUDENT")
    @DisplayName("학생이 아닌 사용자에게는 학생 프로필·지원서를 조회하지 않고 applied 키를 내리지 않는다")
    void omitsAppliedForNonStudent(UserRole role) throws Exception {
        givenActiveUser(role);
        givenJob(job(JobStatus.OPEN));

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value(STORE_NAME))
                .andExpect(jsonPath("$.data", not(hasKey("applied"))));
        verifyNoInteractions(studentRepository, jobApplicationRepository);
    }

    @Test
    @DisplayName("주소를 등록하지 않은 매장은 매장명과 함께 storeAddress를 null로 반환한다")
    void returnsNullStoreAddressWhenNotRegistered() throws Exception {
        givenActiveStudent();
        givenJobOwnerStore(null);
        givenJob(job(JobStatus.OPEN));

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value(STORE_NAME))
                .andExpect(jsonPath("$.data", hasKey("storeAddress")))
                .andExpect(jsonPath("$.data.storeAddress").value(nullValue()));
    }

    @Test
    @DisplayName("의뢰가 참조하는 사장님 프로필이 없으면 500을 반환한다")
    void rejectsJobWithoutOwnerProfile() throws Exception {
        givenActiveStudent();
        when(ownerRepository.findById(JOB_OWNER_PROFILE_ID)).thenReturn(Optional.empty());
        givenJob(job(JobStatus.OPEN));

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("특기가 없는 의뢰는 specialtyCategories 빈 배열을 반환한다")
    void returnsEmptySpecialtyCategories() throws Exception {
        givenActiveStudent();
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job(JobStatus.OPEN)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of());

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.specialtyCategories").isArray())
                .andExpect(jsonPath("$.data.specialtyCategories").isEmpty());
        verifyNoInteractions(specialtyRepository, specialtyCategoryRepository);
    }

    @Test
    @DisplayName("존재하지 않는 의뢰는 전용 404 응답을 반환한다")
    void returnsNotFound() throws Exception {
        givenActiveStudent();
        when(jobRepository.findById(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSpecialtyRepository, specialtyRepository, specialtyCategoryRepository);
    }

    @Test
    @DisplayName("0 이하 또는 숫자가 아닌 의뢰 ID는 공통 400 응답을 반환한다")
    void rejectsInvalidJobId() throws Exception {
        for (String jobId : List.of("0", "-1", "abc")) {
            mockMvc.perform(get("/jobs/{jobId}", jobId).principal(authentication))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        verifyNoInteractions(userRepository, jobRepository, jobSpecialtyRepository);
    }

    @Test
    @DisplayName("활성 사용자를 찾지 못하면 401을 반환하고 의뢰를 조회하지 않는다")
    void rejectsInactiveUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(jobRepository, jobSpecialtyRepository);
    }

    private ResultActions expectPaidCancellationInfo(ResultActions result) throws Exception {
        return result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.title").value("가게 홍보 웹사이트 제작"))
                .andExpect(jsonPath("$.data.storeName").value(STORE_NAME))
                .andExpect(jsonPath("$.data.storeAddress").value(STORE_ADDRESS))
                .andExpect(jsonPath("$.data.cancelledBy").value("OWNER"))
                .andExpect(jsonPath("$.data.cancelReason").value(CANCEL_REASON))
                .andExpect(jsonPath("$.data.messageToStudent").value(MESSAGE_TO_STUDENT))
                .andExpect(jsonPath("$.data.budget").value(300000))
                // 현재 보상 비율(20%)로 다시 계산한 값이 아니라 저장된 값이어야 한다
                .andExpect(jsonPath("$.data.refundAmount").value(70_000))
                .andExpect(jsonPath("$.data.studentCompensationAmount").value(30_000))
                .andExpect(jsonPath("$.data.cancelledAt").value("2026-10-04T21:00:00+09:00"));
    }

    private void expectHiddenCancellationInfo() throws Exception {
        expectNoCancellationInfo(mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.title").value("가게 홍보 웹사이트 제작"))
                .andExpect(jsonPath("$.data.budget").value(300000))
                // 매장 정보는 취소 정보가 아니므로 당사자가 아니어도 내린다
                .andExpect(jsonPath("$.data.storeName").value(STORE_NAME))
                .andExpect(jsonPath("$.data.storeAddress").value(STORE_ADDRESS)));
        verifyNoInteractions(paymentRepository);
    }

    private void expectNoCancellationInfo(ResultActions result) throws Exception {
        for (String field : List.of("cancelledBy", "cancelReason", "messageToStudent",
                "refundAmount", "studentCompensationAmount", "cancelledAt")) {
            result.andExpect(jsonPath("$.data." + field).value(nullValue()));
        }
    }

    private void givenActiveStudent() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false))
                .thenReturn(Optional.of(User.builder().username(USERNAME).role(UserRole.STUDENT).isLock(false).build()));
    }

    private void givenActiveUser(UserRole role) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(USER_ID).username(USERNAME).role(role).isLock(false).build()));
    }

    private void givenOwnerProfileOfUser(Long ownerProfileId) {
        when(ownerRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(Owner.builder().id(ownerProfileId).userId(USER_ID).build()));
    }

    private void givenStudentProfileOfUser(Long studentProfileId) {
        when(studentRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(Student.builder().id(studentProfileId).userId(USER_ID).build()));
    }

    @Test
    @DisplayName("의뢰 상세는 여러 참고 사진 URL을 저장된 순서대로 반환한다")
    void returnsReferenceImagesInOrder() throws Exception {
        givenActiveStudent();
        givenJob(jobBuilder(JobStatus.OPEN)
                .referenceImageUrls(List.of("https://images.example.com/b.png", "https://images.example.com/a.png"))
                .build());

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referenceImageUrls.length()").value(2))
                .andExpect(jsonPath("$.data.referenceImageUrls[0]").value("https://images.example.com/b.png"))
                .andExpect(jsonPath("$.data.referenceImageUrls[1]").value("https://images.example.com/a.png"));
    }

    @Test
    @DisplayName("참고 사진이 없는 기존 의뢰는 상세에서 빈 배열을 반환한다")
    void returnsEmptyReferenceImages() throws Exception {
        givenActiveStudent();
        givenJob(job(JobStatus.OPEN));

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referenceImageUrls").isArray())
                .andExpect(jsonPath("$.data.referenceImageUrls").isEmpty());
    }

    private void givenJobOwnerStore(String storeAddress) {
        when(ownerRepository.findById(JOB_OWNER_PROFILE_ID)).thenReturn(Optional.of(Owner.builder()
                .id(JOB_OWNER_PROFILE_ID).storeName(STORE_NAME).storeAddress(storeAddress).build()));
    }

    private void givenApplication(Long studentProfileId, JobApplicationStatus status) {
        when(jobApplicationRepository.findByStudentProfileIdAndJobIdIn(studentProfileId, List.of(42L)))
                .thenReturn(List.of(JobApplication.builder()
                        .jobId(42L).studentProfileId(studentProfileId).status(status).build()));
    }

    private void givenJob(Job job) {
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of());
    }

    private void givenRefundedPayment(Long refundAmount, Long studentCompensationAmount) {
        Payment payment = mock(Payment.class);
        when(payment.getAmount()).thenReturn(100_000L);
        when(payment.getRefundAmount()).thenReturn(refundAmount);
        when(payment.getStudentCompensationAmount()).thenReturn(studentCompensationAmount);
        when(paymentRepository.findByJobIdAndStatus(42L, PaymentStatus.REFUNDED)).thenReturn(Optional.of(payment));
    }

    /** 사장님이 취소한 의뢰. 선정 학생이 있으면 결제 후(진행 중) 취소, 없으면 결제 전(모집 중) 취소다. */
    private Job cancelledJob(Long selectedStudentProfileId) {
        return jobBuilder(JobStatus.CANCELLED)
                .selectedStudentProfileId(selectedStudentProfileId)
                .completedAt(CANCELLED_AT)
                .cancelReason(CANCEL_REASON)
                .messageToStudent(MESSAGE_TO_STUDENT)
                .build();
    }

    private Job job(JobStatus status) {
        return jobBuilder(status).build();
    }

    private Job.JobBuilder jobBuilder(JobStatus status) {
        return Job.builder()
                .id(42L)
                .ownerProfileId(JOB_OWNER_PROFILE_ID)
                .title("가게 홍보 웹사이트 제작")
                .description("메뉴와 위치를 소개하는 웹사이트가 필요합니다.")
                .budget(300000L)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .revisionCount(1)
                .status(status);
    }

    private Specialty specialty(Long id, Long categoryId, String name) {
        return Specialty.builder().id(id).specialtyCategoryId(categoryId).name(name).build();
    }

    private SpecialtyCategory category(Long id, String name) {
        return SpecialtyCategory.builder().id(id).name(name).build();
    }

    @ParameterizedTest(name = "조회자 {0}, 의뢰 {1}")
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "null, 01K6DEMO00000000000000000A",
            "01K6DEMO00000000000000000A, null",
            "01K6DEMO00000000000000000A, 01K6DEMO00000000000000000B"}, nullValues = "null")
    @DisplayName("조회자와 격리 범위가 다른 의뢰 상세는 없는 의뢰와 같은 404 JOB_404를 반환한다")
    void hidesJobOutsideViewerDemoSession(String viewerSession, String jobSession) throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(User.builder()
                .id(USER_ID).username(USERNAME).role(UserRole.STUDENT).isLock(false)
                .demoSessionId(viewerSession).build()));
        when(jobRepository.findById(42L))
                .thenReturn(Optional.of(jobBuilder(JobStatus.OPEN).demoSessionId(jobSession).build()));

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
    }

    @Test
    @DisplayName("같은 데모 세션의 의뢰 상세는 조회된다")
    void returnsJobWithinViewerDemoSession() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(User.builder()
                .id(USER_ID).username(USERNAME).role(UserRole.STUDENT).isLock(false)
                .demoSessionId("01K6DEMO00000000000000000A").build()));
        when(jobRepository.findById(42L)).thenReturn(Optional.of(
                jobBuilder(JobStatus.OPEN).demoSessionId("01K6DEMO00000000000000000A").build()));

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = UserRole.class, names = { "OWNER", "STUDENT" })
    @DisplayName("학생이 거절한 제안 의뢰의 상세는 의뢰한 사장님과 거절한 학생에게 cancelledBy STUDENT와 전액 환불·보상금 0원·거절 시각을 반환한다")
    void returnsStudentAsCancellerOfDeclinedJob(UserRole viewerRole) throws Exception {
        givenActiveUser(viewerRole);
        if (viewerRole == UserRole.OWNER) {
            givenOwnerProfileOfUser(JOB_OWNER_PROFILE_ID);
        } else {
            givenStudentProfileOfUser(SELECTED_STUDENT_PROFILE_ID);
        }
        Job declined = Job.createAwaitingStart(JOB_OWNER_PROFILE_ID, SELECTED_STUDENT_PROFILE_ID, 5L,
                "메뉴판 개선 제안", "설명", 100_000L, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 20), 1,
                "잘 부탁드립니다.", null);
        declined.decline(CANCELLED_AT);
        givenJob(declined);
        givenRefundedPayment(100_000L, 0L);

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.progressStage").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledBy").value("STUDENT"))
                .andExpect(jsonPath("$.data.cancelReason").value("학생이 작업 시작 전에 의뢰서를 거절했습니다."))
                .andExpect(jsonPath("$.data.messageToStudent").value(nullValue()))
                .andExpect(jsonPath("$.data.refundAmount").value(100_000))
                .andExpect(jsonPath("$.data.studentCompensationAmount").value(0))
                .andExpect(jsonPath("$.data.cancelledAt").value("2026-10-04T21:00:00+09:00"))
                // 작업 조건은 그대로 남는다
                .andExpect(jsonPath("$.data.budget").value(100_000))
                .andExpect(jsonPath("$.data.draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.finalDeadline").value("2026-10-20"));
    }

    @Test
    @DisplayName("작업을 시작한 뒤 사장님이 취소한 제안 의뢰의 상세는 cancelledBy OWNER를 유지한다")
    void keepsOwnerAsCancellerOfStartedProposalJob() throws Exception {
        givenActiveUser(UserRole.STUDENT);
        givenStudentProfileOfUser(SELECTED_STUDENT_PROFILE_ID);
        givenJob(jobBuilder(JobStatus.CANCELLED).proposalId(5L).selectedStudentProfileId(SELECTED_STUDENT_PROFILE_ID)
                .startedAt(CANCELLED_AT.minusDays(1)).completedAt(CANCELLED_AT).cancelReason(CANCEL_REASON)
                .messageToStudent(MESSAGE_TO_STUDENT).build());
        givenRefundedPayment(70_000L, 30_000L);

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelledBy").value("OWNER"))
                .andExpect(jsonPath("$.data.studentCompensationAmount").value(30_000));
    }
}
