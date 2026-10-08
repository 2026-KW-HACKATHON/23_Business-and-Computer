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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;
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

@DisplayName("사장님·학생 제출 이력 조회 전체 흐름 (GET /jobs/{jobId}/submissions)")
class JobSubmissionHistoryFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB6D";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB6E";
    private static final String URL = "/jobs/42/submissions";
    private static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2026, 10, 1, 9, 30, 0);
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 2, 14, 5, 30);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private final JobSubmissionFileStorageClient storageClient = mock(JobSubmissionFileStorageClient.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, mock(JwtService.class));
        JobService jobService = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
                mock(JobApplicationRepository.class), jobSubmissionRepository,
                Clock.systemUTC());
        JobFacade facade = new JobFacade(userService, new OwnerService(ownerRepository), jobService,
                mock(SpecialtyCategoryService.class), mock(SpecialtyService.class), new StudentService(studentRepository),
                storageClient, mock(ChatAttachmentPolicy.class), paymentService,
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class), mock(ApplicationEventPublisher.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = { "OWNER", "STUDENT" })
    @DisplayName("사장님과 담당 학생은 같은 초안·수정안 1·수정안 2를 수정 번호 순서로 받고 각 수정 요청은 그 요청을 받은 제출물에만 붙는다")
    void returnsFullHistoryToBothParties(UserRole role) throws Exception {
        givenViewer(role);
        givenJob(JobStatus.MATCHED, 7L);
        givenSubmissions(
                submission(81L, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.REVISION_REQUESTED)
                        .message("초안입니다.")
                        .reviewComment("로고를 조금 더 크게 해주세요.")
                        .revisionReferenceImageUrls(
                                List.of("https://images.example.com/b.png", "https://images.example.com/a.png"))
                        .reviewedAt(REQUESTED_AT)
                        .build(),
                submission(82L, JobSubmissionType.REVISION, 1, JobSubmissionReviewStatus.REVISION_REQUESTED)
                        .message("첫 수정안입니다.")
                        .createdAt(SUBMITTED_AT.plusDays(2))
                        .reviewComment("색을 더 밝게 해주세요.")
                        .reviewedAt(REQUESTED_AT.plusDays(2))
                        .build(),
                submission(83L, JobSubmissionType.REVISION, 2, JobSubmissionReviewStatus.PENDING)
                        .message("두 번째 수정안입니다.")
                        .createdAt(SUBMITTED_AT.plusDays(4))
                        .build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.submissions.length()").value(3))
                .andExpect(jsonPath("$.data.submissions[*].submissionId").value(contains(81, 82, 83)))
                .andExpect(jsonPath("$.data.submissions[*].submissionType")
                        .value(contains("DRAFT", "REVISION", "REVISION")))
                .andExpect(jsonPath("$.data.submissions[*].revisionNumber").value(contains(0, 1, 2)))
                .andExpect(jsonPath("$.data.submissions[*].reviewStatus")
                        .value(contains("REVISION_REQUESTED", "REVISION_REQUESTED", "PENDING")))
                .andExpect(jsonPath("$.data.submissions[*].message")
                        .value(contains("초안입니다.", "첫 수정안입니다.", "두 번째 수정안입니다.")))
                .andExpect(jsonPath("$.data.submissions[*].submittedAt").value(contains(
                        "2026-10-01T18:30:00+09:00", "2026-10-03T18:30:00+09:00", "2026-10-05T18:30:00+09:00")))
                .andExpect(jsonPath("$.data.submissions[0].revisionRequest.message")
                        .value("로고를 조금 더 크게 해주세요."))
                .andExpect(jsonPath("$.data.submissions[0].revisionRequest.referenceImageUrls")
                        .value(contains("https://images.example.com/b.png", "https://images.example.com/a.png")))
                .andExpect(jsonPath("$.data.submissions[0].revisionRequest.requestedAt")
                        .value("2026-10-02T23:05:30+09:00"))
                .andExpect(jsonPath("$.data.submissions[1].revisionRequest.message").value("색을 더 밝게 해주세요."))
                .andExpect(jsonPath("$.data.submissions[1].revisionRequest.referenceImageUrls.length()").value(0))
                .andExpect(jsonPath("$.data.submissions[1].revisionRequest.requestedAt")
                        .value("2026-10-04T23:05:30+09:00"))
                .andExpect(jsonPath("$.data.submissions[2]", hasKey("revisionRequest")))
                .andExpect(jsonPath("$.data.submissions[2].revisionRequest").value(nullValue()));
        verifyNoInteractions(storageClient, paymentService);
    }

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    @DisplayName("작업 상태와 무관하게 사장님은 본인 의뢰의 제출 이력을 조회한다")
    void returnsHistoryToOwnerInEveryJobStatus(JobStatus status) throws Exception {
        givenViewer(UserRole.OWNER);
        givenJob(status, 7L);
        givenSubmissions(submission(81L, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.PENDING).build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissions[*].submissionId").value(contains(81)));
        verifyNoInteractions(studentRepository);
    }

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    @DisplayName("작업 상태와 무관하게 담당 학생은 본인 의뢰의 제출 이력을 조회한다")
    void returnsHistoryToStudentInEveryJobStatus(JobStatus status) throws Exception {
        givenViewer(UserRole.STUDENT);
        givenJob(status, 7L);
        givenSubmissions(submission(81L, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.PENDING).build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissions[*].submissionId").value(contains(81)));
        verifyNoInteractions(ownerRepository);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = { "OWNER", "STUDENT" })
    @DisplayName("제출 전인 본인 의뢰는 200과 빈 submissions 배열을 반환한다")
    void returnsEmptyListBeforeFirstSubmission(UserRole role) throws Exception {
        givenViewer(role);
        givenJob(JobStatus.MATCHED, 7L);
        givenSubmissions();

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.submissions").isArray())
                .andExpect(jsonPath("$.data.submissions.length()").value(0));
    }

    @Test
    @DisplayName("승인된 최종 제출물은 revisionRequest를 null로 내리고 이전 차수의 수정 요청은 이전 제출물에 남긴다")
    void returnsApprovedSubmissionWithoutRevisionRequest() throws Exception {
        givenViewer(UserRole.OWNER);
        givenJob(JobStatus.CLOSED, 7L);
        givenSubmissions(
                submission(81L, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.REVISION_REQUESTED)
                        .reviewComment("로고를 조금 더 크게 해주세요.")
                        .reviewedAt(REQUESTED_AT)
                        .build(),
                submission(82L, JobSubmissionType.REVISION, 1, JobSubmissionReviewStatus.APPROVED)
                        .reviewedAt(REQUESTED_AT.plusDays(2))
                        .build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissions[0].revisionRequest.message")
                        .value("로고를 조금 더 크게 해주세요."))
                .andExpect(jsonPath("$.data.submissions[1].reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.submissions[1]", hasKey("revisionRequest")))
                .andExpect(jsonPath("$.data.submissions[1].revisionRequest").value(nullValue()));
    }

    @Test
    @DisplayName("파일과 크기를 저장 순서대로 반환하고 크기를 기록하지 않은 과거 파일은 size를 null로 내리며 저장소를 조회하지 않는다")
    void returnsFilesInStoredOrderWithSizes() throws Exception {
        givenViewer(UserRole.STUDENT);
        givenJob(JobStatus.MATCHED, 7L);
        givenSubmissions(
                submission(81L, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.REVISION_REQUESTED).build(),
                submission(82L, JobSubmissionType.REVISION, 1, JobSubmissionReviewStatus.PENDING)
                        .fileUrls(List.of("https://example.com/b.png", "https://example.com/a.pdf"))
                        .fileSizes(Map.of("https://example.com/a.pdf", 1048576L, "https://example.com/b.png", 2048L))
                        .build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissions[0].fileUrls").value(contains("https://example.com/draft.pdf")))
                .andExpect(jsonPath("$.data.submissions[0].files.length()").value(1))
                .andExpect(jsonPath("$.data.submissions[0].files[0].fileUrl").value("https://example.com/draft.pdf"))
                .andExpect(jsonPath("$.data.submissions[0].files[0]", hasKey("size")))
                .andExpect(jsonPath("$.data.submissions[0].files[0].size").value(nullValue()))
                .andExpect(jsonPath("$.data.submissions[1].fileUrls")
                        .value(contains("https://example.com/b.png", "https://example.com/a.pdf")))
                .andExpect(jsonPath("$.data.submissions[1].files[*].fileUrl")
                        .value(contains("https://example.com/b.png", "https://example.com/a.pdf")))
                .andExpect(jsonPath("$.data.submissions[1].files[*].size").value(contains(2048, 1048576)));
        verifyNoInteractions(storageClient);
    }

    @Test
    @DisplayName("내용과 시각이 기록되지 않은 과거 수정 요청은 message와 requestedAt을 null, 참고 이미지를 빈 배열로 반환한다")
    void returnsLegacyRevisionRequest() throws Exception {
        givenViewer(UserRole.OWNER);
        givenJob(JobStatus.MATCHED, 7L);
        givenSubmissions(
                submission(81L, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.REVISION_REQUESTED).build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissions[0].revisionRequest", hasKey("message")))
                .andExpect(jsonPath("$.data.submissions[0].revisionRequest.message").value(nullValue()))
                .andExpect(jsonPath("$.data.submissions[0].revisionRequest.referenceImageUrls").isArray())
                .andExpect(jsonPath("$.data.submissions[0].revisionRequest.referenceImageUrls.length()").value(0))
                .andExpect(jsonPath("$.data.submissions[0].revisionRequest", hasKey("requestedAt")))
                .andExpect(jsonPath("$.data.submissions[0].revisionRequest.requestedAt").value(nullValue()));
    }

    @Test
    @DisplayName("다른 사장님의 의뢰이면 404 JOB_404를 반환하고 제출물을 조회하지 않는다")
    void rejectsOtherOwnersJob() throws Exception {
        givenOwner(6L);
        givenJob(JobStatus.MATCHED, 7L);

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("다른 학생이 담당한 의뢰이면 404 JOB_404를 반환하고 제출물을 조회하지 않는다")
    void rejectsOtherStudentsJob() throws Exception {
        givenViewer(UserRole.STUDENT);
        givenJob(JobStatus.MATCHED, 8L);

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("담당 학생이 정해지지 않은 모집 중 의뢰를 선정되지 않은 학생이 조회하면 404 JOB_404를 반환하고 제출물을 조회하지 않는다")
    void rejectsUnselectedStudent() throws Exception {
        givenViewer(UserRole.STUDENT);
        givenJob(JobStatus.OPEN, null);

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("사장님 프로필 ID가 담당 학생 프로필 ID와 숫자만 같으면 404 JOB_404로 거부하고 제출물을 조회하지 않는다")
    void rejectsOwnerWhoseProfileIdEqualsSelectedStudentId() throws Exception {
        givenOwner(7L);
        givenJob(JobStatus.MATCHED, 7L);

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(studentRepository, jobSubmissionRepository);
    }

    @Test
    @DisplayName("학생 프로필 ID가 의뢰한 사장님 프로필 ID와 숫자만 같으면 404 JOB_404로 거부하고 제출물을 조회하지 않는다")
    void rejectsStudentWhoseProfileIdEqualsOwnerId() throws Exception {
        givenViewer(UserRole.STUDENT);
        when(jobRepository.findById(42L)).thenReturn(Optional.of(Job.builder()
                .id(42L)
                .ownerProfileId(7L)
                .status(JobStatus.MATCHED)
                .selectedStudentProfileId(8L)
                .revisionCount(2)
                .build()));

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(ownerRepository, jobSubmissionRepository);
    }

    @Test
    @DisplayName("존재하지 않는 의뢰이면 404 JOB_404를 반환하고 제출물을 조회하지 않는다")
    void rejectsMissingJob() throws Exception {
        givenViewer(UserRole.OWNER);
        when(jobRepository.findById(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("사장님·학생이 아닌 역할은 403 JOB_SUBMISSION_403_VIEW로 거부하고 프로필과 의뢰를 조회하지 않는다")
    void rejectsDisallowedRole() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.PENDING).build()));

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_403_VIEW"));
        verifyNoInteractions(ownerRepository, studentRepository, jobRepository, jobSubmissionRepository);
    }

    @Test
    @DisplayName("사장님 역할이어도 사장님 프로필이 없으면 403 JOB_SUBMISSION_403_VIEW로 거부하고 의뢰를 조회하지 않는다")
    void rejectsOwnerWithoutProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_403_VIEW"));
        verifyNoInteractions(studentRepository, jobRepository, jobSubmissionRepository);
    }

    @Test
    @DisplayName("학생 역할이어도 학생 프로필이 없으면 403 JOB_SUBMISSION_403_VIEW로 거부하고 의뢰를 조회하지 않는다")
    void rejectsStudentWithoutProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_403_VIEW"));
        verifyNoInteractions(ownerRepository, jobRepository, jobSubmissionRepository);
    }

    @Test
    @DisplayName("존재하지 않거나 잠긴 사용자는 401 COMMON_401을 반환하고 의뢰를 조회하지 않는다")
    void rejectsInactiveUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(jobRepository, jobSubmissionRepository);
    }

    @Test
    @DisplayName("의뢰 ID가 0 이하이거나 숫자가 아니면 400 COMMON_400을 반환하고 조회하지 않는다")
    void rejectsInvalidJobId() throws Exception {
        mockMvc.perform(get("/jobs/0/submissions").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        mockMvc.perform(get("/jobs/-1/submissions").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        mockMvc.perform(get("/jobs/abc/submissions").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository, jobSubmissionRepository);
    }

    /** 의뢰 42의 당사자(사장님 프로필 5, 담당 학생 프로필 7)로 로그인한다. */
    private void givenViewer(UserRole role) {
        if (role == UserRole.OWNER) {
            givenOwner(5L);
            return;
        }
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID))
                .thenReturn(Optional.of(Student.builder().id(7L).userId(STUDENT_USER_ID).build()));
    }

    private void givenOwner(Long ownerProfileId) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID))
                .thenReturn(Optional.of(Owner.builder().id(ownerProfileId).userId(OWNER_USER_ID).build()));
    }

    private void givenJob(JobStatus status, Long selectedStudentProfileId) {
        when(jobRepository.findById(42L)).thenReturn(Optional.of(Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .status(status)
                .selectedStudentProfileId(selectedStudentProfileId)
                .revisionCount(2)
                .build()));
    }

    private void givenSubmissions(JobSubmission... submissions) {
        when(jobSubmissionRepository.findByJobIdOrderByRevisionNumberAsc(42L)).thenReturn(List.of(submissions));
    }

    private JobSubmission.JobSubmissionBuilder submission(
            Long id, JobSubmissionType type, int revisionNumber, JobSubmissionReviewStatus reviewStatus) {
        return JobSubmission.builder()
                .id(id)
                .jobId(42L)
                .submissionType(type)
                .revisionNumber(revisionNumber)
                .fileUrls(List.of("https://example.com/draft.pdf"))
                .message("제출합니다.")
                .reviewStatus(reviewStatus)
                .createdAt(SUBMITTED_AT);
    }
}
