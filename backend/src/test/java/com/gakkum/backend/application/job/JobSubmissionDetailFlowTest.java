package com.gakkum.backend.application.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
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
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.service.PaymentService;
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

@DisplayName("사장님 제출물 상세 조회 전체 흐름 (GET /jobs/{jobId}/submission)")
class JobSubmissionDetailFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB6D";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, mock(JwtService.class));
        JobService jobService = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
                mock(JobApplicationRepository.class), jobSubmissionRepository,
                Clock.systemUTC());
        JobFacade facade = new JobFacade(userService, new OwnerService(ownerRepository), jobService,
                mock(SpecialtyCategoryService.class), mock(SpecialtyService.class), new StudentService(studentRepository),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class), mock(PaymentService.class),
                mock(ReviewService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("초안 제출물을 파일 목록, 메시지, 수정 번호 0과 함께 반환한다")
    void returnsDraftSubmission() throws Exception {
        givenOwnerWithStudent();
        givenPendingSubmission(JobSubmission.create(42L, JobSubmissionType.DRAFT, 0,
                List.of("https://example.com/draft.pdf"), "초안입니다."));

        mockMvc.perform(get("/jobs/42/submission").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.title").value("가게 홍보 웹사이트 제작"))
                .andExpect(jsonPath("$.data.studentName").value("홍길동"))
                .andExpect(jsonPath("$.data.submissionType").value("DRAFT"))
                .andExpect(jsonPath("$.data.fileUrls.length()").value(1))
                .andExpect(jsonPath("$.data.fileUrls[0]").value("https://example.com/draft.pdf"))
                .andExpect(jsonPath("$.data.message").value("초안입니다."))
                .andExpect(jsonPath("$.data.revisionNumber").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").doesNotExist())
                .andExpect(jsonPath("$.data.revisionCount").doesNotExist());
    }

    @Test
    @DisplayName("수정 요청에 쓸 수 있도록 검토 대기 제출물의 submissionId를 함께 반환한다")
    void returnsSubmissionId() throws Exception {
        givenOwnerWithStudent();
        givenPendingSubmission(JobSubmission.builder()
                .id(81L)
                .jobId(42L)
                .submissionType(JobSubmissionType.DRAFT)
                .revisionNumber(0)
                .fileUrls(List.of("https://example.com/draft.pdf"))
                .message("초안입니다.")
                .reviewStatus(JobSubmissionReviewStatus.PENDING)
                .build());

        mockMvc.perform(get("/jobs/42/submission").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(81));
    }

    @Test
    @DisplayName("수정안 제출물을 수정 번호와 저장된 파일 순서 그대로 반환한다")
    void returnsRevisionSubmissionPreservingFileOrder() throws Exception {
        givenOwnerWithStudent();
        givenPendingSubmission(JobSubmission.create(42L, JobSubmissionType.REVISION, 1,
                List.of("https://example.com/result-2.png", "https://example.com/result-1.pdf",
                        "https://example.com/result-3.zip"),
                "요청해주신 내용을 반영했습니다."));

        mockMvc.perform(get("/jobs/42/submission").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionType").value("REVISION"))
                .andExpect(jsonPath("$.data.revisionNumber").value(1))
                .andExpect(jsonPath("$.data.fileUrls[0]").value("https://example.com/result-2.png"))
                .andExpect(jsonPath("$.data.fileUrls[1]").value("https://example.com/result-1.pdf"))
                .andExpect(jsonPath("$.data.fileUrls[2]").value("https://example.com/result-3.zip"))
                .andExpect(jsonPath("$.data.message").value("요청해주신 내용을 반영했습니다."));
    }

    @Test
    @DisplayName("선택된 학생 프로필이 없으면 공통 500을 반환한다")
    void failsWhenStudentProfileMissing() throws Exception {
        givenActiveOwner();
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(job()));
        givenPendingSubmission(JobSubmission.create(42L, JobSubmissionType.DRAFT, 0,
                List.of("https://example.com/draft.pdf"), "초안입니다."));
        when(studentRepository.findById(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/jobs/42/submission").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("학생의 사용자 이름이 없으면 공통 500을 반환한다")
    void failsWhenStudentNameMissing() throws Exception {
        givenOwnerWithStudent();
        givenPendingSubmission(JobSubmission.create(42L, JobSubmissionType.DRAFT, 0,
                List.of("https://example.com/draft.pdf"), "초안입니다."));
        when(userRepository.findById(STUDENT_USER_ID))
                .thenReturn(Optional.of(User.builder().id(STUDENT_USER_ID).build()));

        mockMvc.perform(get("/jobs/42/submission").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("존재하지 않거나 다른 사장님의 의뢰는 JOB_404를 반환한다")
    void rejectsJobNotOwned() throws Exception {
        givenActiveOwner();
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/jobs/42/submission").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSubmissionRepository, studentRepository);
    }

    @Test
    @DisplayName("본인 의뢰에 검토 대기 제출물이 없으면 JOB_SUBMISSION_404를 반환한다")
    void rejectsMissingPendingSubmission() throws Exception {
        givenActiveOwner();
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(job()));
        when(jobSubmissionRepository.findByJobIdAndReviewStatus(42L, JobSubmissionReviewStatus.PENDING))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/jobs/42/submission").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_404"));
        verifyNoInteractions(studentRepository);
    }

    @Test
    @DisplayName("사장님 프로필이 없으면 403을 반환하고 의뢰를 조회하지 않는다")
    void rejectsUserWithoutOwnerProfile() throws Exception {
        givenActiveUser();
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/jobs/42/submission").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OWNER_403"));
        verifyNoInteractions(jobRepository, jobSubmissionRepository);
    }

    @Test
    @DisplayName("활성 사용자를 찾지 못하면 401을 반환하고 의뢰를 조회하지 않는다")
    void rejectsInactiveUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        mockMvc.perform(get("/jobs/42/submission").principal(authentication))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(ownerRepository, jobRepository, jobSubmissionRepository);
    }

    @Test
    @DisplayName("0 이하 또는 숫자가 아닌 의뢰 ID는 공통 400 응답을 반환한다")
    void rejectsInvalidJobId() throws Exception {
        for (String jobId : List.of("0", "-1", "abc")) {
            mockMvc.perform(get("/jobs/{jobId}/submission", jobId).principal(authentication))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        verifyNoInteractions(userRepository, jobRepository, jobSubmissionRepository);
    }

    private void givenActiveUser() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(User.builder()
                .id(OWNER_USER_ID).username(USERNAME).role(UserRole.OWNER).isLock(false).build()));
    }

    private void givenActiveOwner() {
        givenActiveUser();
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.of(Owner.builder().id(5L).build()));
    }

    private void givenOwnerWithStudent() {
        givenActiveOwner();
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(job()));
        when(studentRepository.findById(7L))
                .thenReturn(Optional.of(Student.builder().id(7L).userId(STUDENT_USER_ID).build()));
        when(userRepository.findById(STUDENT_USER_ID))
                .thenReturn(Optional.of(User.builder().id(STUDENT_USER_ID).name("홍길동").build()));
    }

    private void givenPendingSubmission(JobSubmission submission) {
        when(jobSubmissionRepository.findByJobIdAndReviewStatus(42L, JobSubmissionReviewStatus.PENDING))
                .thenReturn(Optional.of(submission));
    }

    private Job job() {
        return Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .title("가게 홍보 웹사이트 제작")
                .status(JobStatus.MATCHED)
                .revisionCount(2)
                .selectedStudentProfileId(7L)
                .build();
    }
}
