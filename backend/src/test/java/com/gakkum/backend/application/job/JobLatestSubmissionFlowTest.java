package com.gakkum.backend.application.job;

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

@DisplayName("학생 최신 제출물 조회 전체 흐름 (GET /jobs/{jobId}/submissions/latest)")
class JobLatestSubmissionFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB6D";
    private static final String URL = "/jobs/42/submissions/latest";
    private static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2026, 10, 1, 9, 30, 0);
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 2, 14, 5, 30);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
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
        JobFacade facade = new JobFacade(userService, new OwnerService(mock(OwnerRepository.class)), jobService,
                mock(SpecialtyCategoryService.class), mock(SpecialtyService.class), new StudentService(studentRepository),
                storageClient, mock(ChatAttachmentPolicy.class), mock(PaymentService.class),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("검토 대기 초안을 파일 순서·제출 메시지·제출 시각과 함께 반환하고 revisionRequest는 null로 내린다")
    void returnsPendingDraft() throws Exception {
        givenStudent();
        givenJob(JobStatus.MATCHED, 7L);
        givenLatest(submission(JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.PENDING)
                .fileUrls(List.of("https://example.com/b.png", "https://example.com/a.pdf"))
                .build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.submissionId").value(81))
                .andExpect(jsonPath("$.data.submissionType").value("DRAFT"))
                .andExpect(jsonPath("$.data.revisionNumber").value(0))
                .andExpect(jsonPath("$.data.fileUrls.length()").value(2))
                .andExpect(jsonPath("$.data.fileUrls[0]").value("https://example.com/b.png"))
                .andExpect(jsonPath("$.data.fileUrls[1]").value("https://example.com/a.pdf"))
                .andExpect(jsonPath("$.data.message").value("제출합니다."))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.submittedAt").value("2026-10-01T18:30:00+09:00"))
                .andExpect(jsonPath("$.data", hasKey("revisionRequest")))
                .andExpect(jsonPath("$.data.revisionRequest").value(nullValue()));
    }

    @Test
    @DisplayName("파일별 크기를 fileUrls 순서대로 연결해 반환하고 기존 fileUrls는 유지하며 저장소를 조회하지 않는다")
    void returnsFilesWithSizes() throws Exception {
        givenStudent();
        givenJob(JobStatus.MATCHED, 7L);
        givenLatest(submission(JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.PENDING)
                .fileUrls(List.of("https://example.com/b.png", "https://example.com/a.pdf"))
                .fileSizes(Map.of("https://example.com/a.pdf", 1048576L, "https://example.com/b.png", 2048L))
                .build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileUrls[0]").value("https://example.com/b.png"))
                .andExpect(jsonPath("$.data.fileUrls[1]").value("https://example.com/a.pdf"))
                .andExpect(jsonPath("$.data.files.length()").value(2))
                .andExpect(jsonPath("$.data.files[0].fileUrl").value("https://example.com/b.png"))
                .andExpect(jsonPath("$.data.files[0].size").value(2048))
                .andExpect(jsonPath("$.data.files[1].fileUrl").value("https://example.com/a.pdf"))
                .andExpect(jsonPath("$.data.files[1].size").value(1048576));
        verifyNoInteractions(storageClient);
    }

    @Test
    @DisplayName("크기를 기록하기 전에 제출된 파일은 size를 생략하지 않고 null로 반환한다")
    void returnsLegacyFilesWithNullSize() throws Exception {
        givenStudent();
        givenJob(JobStatus.MATCHED, 7L);
        givenLatest(submission(JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.PENDING).build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileUrls[0]").value("https://example.com/draft.pdf"))
                .andExpect(jsonPath("$.data.files.length()").value(1))
                .andExpect(jsonPath("$.data.files[0].fileUrl").value("https://example.com/draft.pdf"))
                .andExpect(jsonPath("$.data.files[0]", hasKey("size")))
                .andExpect(jsonPath("$.data.files[0].size").value(nullValue()));
        verifyNoInteractions(storageClient);
    }

    @Test
    @DisplayName("수정 요청을 받은 제출물은 요청 내용·참고 사진 순서·요청 시각을 함께 반환하고 학생의 제출 메시지는 그대로 둔다")
    void returnsRevisionRequest() throws Exception {
        givenStudent();
        givenJob(JobStatus.MATCHED, 7L);
        givenLatest(submission(JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.REVISION_REQUESTED)
                .reviewComment("로고를 조금 더 크게 해주세요.")
                .revisionReferenceImageUrls(List.of("https://images.example.com/b.png", "https://images.example.com/a.png"))
                .reviewedAt(REQUESTED_AT)
                .build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("REVISION_REQUESTED"))
                .andExpect(jsonPath("$.data.message").value("제출합니다."))
                .andExpect(jsonPath("$.data.revisionRequest.message").value("로고를 조금 더 크게 해주세요."))
                .andExpect(jsonPath("$.data.revisionRequest.referenceImageUrls.length()").value(2))
                .andExpect(jsonPath("$.data.revisionRequest.referenceImageUrls[0]")
                        .value("https://images.example.com/b.png"))
                .andExpect(jsonPath("$.data.revisionRequest.referenceImageUrls[1]")
                        .value("https://images.example.com/a.png"))
                .andExpect(jsonPath("$.data.revisionRequest.requestedAt").value("2026-10-02T23:05:30+09:00"));
    }

    @Test
    @DisplayName("내용과 시각이 기록되지 않은 과거 수정 요청은 message와 requestedAt을 null, 사진을 빈 배열로 반환한다")
    void returnsLegacyRevisionRequest() throws Exception {
        givenStudent();
        givenJob(JobStatus.MATCHED, 7L);
        givenLatest(submission(JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.REVISION_REQUESTED).build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revisionRequest", hasKey("message")))
                .andExpect(jsonPath("$.data.revisionRequest.message").value(nullValue()))
                .andExpect(jsonPath("$.data.revisionRequest.referenceImageUrls").isArray())
                .andExpect(jsonPath("$.data.revisionRequest.referenceImageUrls.length()").value(0))
                .andExpect(jsonPath("$.data.revisionRequest", hasKey("requestedAt")))
                .andExpect(jsonPath("$.data.revisionRequest.requestedAt").value(nullValue()));
    }

    @Test
    @DisplayName("수정안을 다시 제출하면 새 수정안을 반환하고 이전 차수의 수정 요청은 붙이지 않는다")
    void returnsResubmittedRevisionWithoutPreviousRequest() throws Exception {
        givenStudent();
        givenJob(JobStatus.MATCHED, 7L);
        givenLatest(submission(JobSubmissionType.REVISION, 1, JobSubmissionReviewStatus.PENDING).id(82L).build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(82))
                .andExpect(jsonPath("$.data.submissionType").value("REVISION"))
                .andExpect(jsonPath("$.data.revisionNumber").value(1))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data", hasKey("revisionRequest")))
                .andExpect(jsonPath("$.data.revisionRequest").value(nullValue()));
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = { "CLOSED", "CANCELLED" })
    @DisplayName("완료·취소된 의뢰도 담당 학생은 최신 제출물을 조회할 수 있다")
    void returnsLatestOfFinishedJob(JobStatus status) throws Exception {
        givenStudent();
        givenJob(status, 7L);
        JobSubmissionReviewStatus reviewStatus = status == JobStatus.CLOSED
                ? JobSubmissionReviewStatus.APPROVED
                : JobSubmissionReviewStatus.REVISION_REQUESTED;
        givenLatest(submission(JobSubmissionType.DRAFT, 0, reviewStatus).build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(81))
                .andExpect(jsonPath("$.data.reviewStatus").value(reviewStatus.name()));
    }

    @Test
    @DisplayName("승인된 제출물은 revisionRequest를 null로 내린다")
    void returnsApprovedWithoutRevisionRequest() throws Exception {
        givenStudent();
        givenJob(JobStatus.CLOSED, 7L);
        givenLatest(submission(JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.APPROVED).build());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("revisionRequest")))
                .andExpect(jsonPath("$.data.revisionRequest").value(nullValue()));
    }

    @Test
    @DisplayName("학생이 아닌 사용자는 403 JOB_SUBMISSION_403_VIEW로 거부하고 의뢰를 조회하지 않는다")
    void rejectsNonStudent() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.OWNER).build()));

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
        verifyNoInteractions(jobRepository, jobSubmissionRepository);
    }

    @Test
    @DisplayName("존재하지 않거나 잠긴 사용자는 401 COMMON_401을 반환한다")
    void rejectsInactiveUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(jobRepository, jobSubmissionRepository);
    }

    @Test
    @DisplayName("존재하지 않는 의뢰이면 404 JOB_404를 반환한다")
    void rejectsMissingJob() throws Exception {
        givenStudent();
        when(jobRepository.findById(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("다른 학생이 담당한 의뢰이면 404 JOB_404를 반환하고 제출물을 조회하지 않는다")
    void rejectsOtherStudentsJob() throws Exception {
        givenStudent();
        givenJob(JobStatus.MATCHED, 8L);

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("담당 학생이 정해지지 않은 모집 중 의뢰이면 404 JOB_404를 반환한다")
    void rejectsJobWithoutSelectedStudent() throws Exception {
        givenStudent();
        givenJob(JobStatus.OPEN, null);

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("본인 의뢰에 제출물이 없으면 404 JOB_SUBMISSION_404_LATEST를 반환한다")
    void rejectsJobWithoutSubmission() throws Exception {
        givenStudent();
        givenJob(JobStatus.MATCHED, 7L);
        when(jobSubmissionRepository.findFirstByJobIdOrderByRevisionNumberDesc(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get(URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_404_LATEST"));
    }

    @Test
    @DisplayName("의뢰 ID가 0 이하이거나 숫자가 아니면 400 COMMON_400을 반환하고 조회하지 않는다")
    void rejectsInvalidJobId() throws Exception {
        mockMvc.perform(get("/jobs/0/submissions/latest").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        mockMvc.perform(get("/jobs/abc/submissions/latest").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository, jobSubmissionRepository);
    }

    private void givenStudent() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID))
                .thenReturn(Optional.of(Student.builder().id(7L).userId(STUDENT_USER_ID).build()));
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

    private void givenLatest(JobSubmission submission) {
        when(jobSubmissionRepository.findFirstByJobIdOrderByRevisionNumberDesc(42L))
                .thenReturn(Optional.of(submission));
    }

    private JobSubmission.JobSubmissionBuilder submission(
            JobSubmissionType type, int revisionNumber, JobSubmissionReviewStatus reviewStatus) {
        return JobSubmission.builder()
                .id(81L)
                .jobId(42L)
                .submissionType(type)
                .revisionNumber(revisionNumber)
                .fileUrls(List.of("https://example.com/draft.pdf"))
                .message("제출합니다.")
                .reviewStatus(reviewStatus)
                .createdAt(SUBMITTED_AT);
    }
}
