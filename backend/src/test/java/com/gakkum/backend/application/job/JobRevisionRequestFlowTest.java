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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;

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
import com.gakkum.backend.domain.job.repository.JobRepository.RevisionRequestTargetProjection;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository.ReviewTargetProjection;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.media.client.MediaImageStorageClient;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;
import com.gakkum.backend.domain.notification.dto.NotificationEventFactory;
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
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;
import com.gakkum.backend.global.transaction.ImmediateTransactionTemplate;

@DisplayName("사장님 수정 요청 전체 흐름 (POST /jobs/{jobId}/submissions/{submissionId}/revision-request)")
class JobRevisionRequestFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final String URL = "/jobs/42/submissions/81/revision-request";
    private static final String MESSAGE = "로고를 조금 더 크게 해주세요.";
    private static final String KEY_PREFIX = "images/job/" + OWNER_USER_ID + "/";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final MediaImageStorageClient imageStorageClient = mock(MediaImageStorageClient.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
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
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class),
                new MediaService(imageStorageClient, DataSize.ofMegabytes(10)), eventPublisher,
                new ImmediateTransactionTemplate(), mock(ChatRoomService.class));
        when(studentRepository.findById(7L)).thenReturn(Optional.of(
                Student.builder().id(7L).userId(STUDENT_USER_ID).build()));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("요청 내용과 참고 사진을 남겨 수정을 요청하면 200과 data 없는 성공 응답을 반환하고 내용·사진·시각을 함께 기록한 뒤 담당 학생에게 알린다")
    void requestsRevision() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.PENDING);
        String first = givenUploadedImage("00000000-0000-0000-0000-000000000002.png");
        String second = givenUploadedImage("00000000-0000-0000-0000-000000000001.png");

        request(body("\"" + MESSAGE + "\"", "[\"" + first + "\",\"" + second + "\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.REVISION_REQUESTED);
        assertThat(submission.getReviewComment()).isEqualTo(MESSAGE);
        assertThat(submission.getRevisionReferenceImageUrls()).containsExactly(first, second);
        assertThat(submission.getReviewedAt()).isNotNull();
        assertThat(submission.getMessage()).isEqualTo("초안입니다.");
        assertThat(submission.getFileUrls()).containsExactly("https://example.com/draft.pdf");
        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(NotificationEventFactory.jobRevisionRequested(
                STUDENT_USER_ID, 81L, 42L, "메뉴판 디자인", "가꿈 카페"));
    }

    @Test
    @DisplayName("요청 내용의 앞뒤 공백은 제거하고 저장한다")
    void trimsMessage() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.PENDING);

        request(body("\"  \\n" + MESSAGE + "  \"", null)).andExpect(status().isOk());
        assertThat(submission.getReviewComment()).isEqualTo(MESSAGE);
    }

    @Test
    @DisplayName("요청 내용이 500자이면 허용한다")
    void acceptsMessageOfMaxLength() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.PENDING);

        request(body("\"" + "가".repeat(500) + "\"", null)).andExpect(status().isOk());
        assertThat(submission.getReviewComment()).hasSize(500);
    }

    @Test
    @DisplayName("본문이 없으면 400 COMMON_400을 반환하고 조회하지 않는다")
    void rejectsMissingBody() throws Exception {
        mockMvc.perform(post(URL).principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        mockMvc.perform(post(URL).principal(authentication).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository, jobSubmissionRepository, imageStorageClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"message\": null}", "{\"message\": \"\"}", "{\"message\": \"  \\n \"}"})
    @DisplayName("요청 내용이 없거나 공백뿐이면 400 COMMON_400을 반환하고 조회하지 않는다")
    void rejectsBlankMessage(String body) throws Exception {
        request(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository, jobSubmissionRepository, imageStorageClient);
    }

    @Test
    @DisplayName("요청 내용이 501자이면 400 COMMON_400을 반환하고 조회하지 않는다")
    void rejectsTooLongMessage() throws Exception {
        request(body("\"" + "가".repeat(501) + "\"", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository, jobSubmissionRepository, imageStorageClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"omitted", "null", "[]"})
    @DisplayName("참고 사진을 생략하거나 null·빈 배열로 보내면 사진 없이 수정을 요청하고 저장소를 조회하지 않는다")
    void acceptsNoReferenceImages(String images) throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.PENDING);

        request(body("\"" + MESSAGE + "\"", "omitted".equals(images) ? null : images)).andExpect(status().isOk());
        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.REVISION_REQUESTED);
        assertThat(submission.getRevisionReferenceImageUrls()).isEmpty();
        verifyNoInteractions(imageStorageClient);
    }

    @Test
    @DisplayName("참고 사진이 5장이면 400 COMMON_400을 반환하고 조회하지 않는다")
    void rejectsTooManyReferenceImages() throws Exception {
        String images = IntStream.rangeClosed(1, 5)
                .mapToObj(index -> "\"" + imageUrl(OWNER_USER_ID, "00000000-0000-0000-0000-00000000000" + index + ".png")
                        + "\"")
                .collect(Collectors.joining(",", "[", "]"));

        request(body("\"" + MESSAGE + "\"", images))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository, jobSubmissionRepository, imageStorageClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[null]", "[\"\"]", "[\"   \"]"})
    @DisplayName("참고 사진 배열에 null이나 빈 문자열·공백이 있으면 400 COMMON_400을 반환하고 조회하지 않는다")
    void rejectsBlankImageElements(String images) throws Exception {
        request(body("\"" + MESSAGE + "\"", images))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository, jobSubmissionRepository, imageStorageClient);
    }

    @Test
    @DisplayName("같은 참고 사진 URL이 중복되면 400 COMMON_400을 반환하고 조회하지 않는다")
    void rejectsDuplicateReferenceImages() throws Exception {
        String url = imageUrl(OWNER_USER_ID, "00000000-0000-0000-0000-000000000001.png");

        request(body("\"" + MESSAGE + "\"", "[\"" + url + "\",\"" + url + "\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository, jobSubmissionRepository, imageStorageClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example.com/image.png",
            "https://images.example.com/images/job/other-user/00000000-0000-0000-0000-000000000001.png",
            "https://images.example.com/images/proposal/01K58M6PJV8VAJMXHBHJ2PNB5C/00000000-0000-0000-0000-000000000001.png",
            "https://images.example.com/images/job/01K58M6PJV8VAJMXHBHJ2PNB5C/photo.png"})
    @DisplayName("외부·타인·다른 용도·발급 형태가 아닌 사진 URL은 400 JOB_400_IMAGE_URL로 거부하고 제출물을 바꾸지 않는다")
    void rejectsUnissuedReferenceImageUrl(String invalidUrl) throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.PENDING);
        String validUrl = givenUploadedImage("00000000-0000-0000-0000-000000000001.png");
        when(imageStorageClient.findKey(invalidUrl, KEY_PREFIX))
                .thenReturn(invalidUrl.endsWith("/photo.png") ? Optional.of(KEY_PREFIX + "photo.png") : Optional.empty());

        request(body("\"" + MESSAGE + "\"", "[\"" + validUrl + "\",\"" + invalidUrl + "\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("JOB_400_IMAGE_URL"));
        verify(imageStorageClient, never()).exists(any());
        assertUnchanged(submission);
    }

    @Test
    @DisplayName("발급된 사진 URL이라도 업로드가 끝나지 않았으면 409 JOB_409_IMAGE_NOT_UPLOADED로 거부하고 제출물을 바꾸지 않는다")
    void rejectsImageNotUploaded() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.PENDING);
        String url = imageUrl(OWNER_USER_ID, "00000000-0000-0000-0000-000000000001.png");
        when(imageStorageClient.findKey(url, KEY_PREFIX)).thenReturn(Optional.of(keyOf(url)));

        request(body("\"" + MESSAGE + "\"", "[\"" + url + "\"]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_409_IMAGE_NOT_UPLOADED"));
        assertUnchanged(submission);
    }

    @Test
    @DisplayName("사진 저장소에 연결하지 못하면 502 MEDIA_UPLOAD_502로 거부하고 제출물을 바꾸지 않는다")
    void rejectsWhenImageStorageUnavailable() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.PENDING);
        String url = imageUrl(OWNER_USER_ID, "00000000-0000-0000-0000-000000000001.png");
        when(imageStorageClient.findKey(url, KEY_PREFIX)).thenReturn(Optional.of(keyOf(url)));
        when(imageStorageClient.exists(keyOf(url))).thenThrow(new BusinessException(ErrorCode.MEDIA_UPLOAD_UNAVAILABLE));

        request(body("\"" + MESSAGE + "\"", "[\"" + url + "\"]"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("MEDIA_UPLOAD_502"));
        assertUnchanged(submission);
    }

    @Test
    @DisplayName("같은 제출물에 다시 수정을 요청하면 사진 저장소 확인 전에 409 JOB_SUBMISSION_409_REVIEWED를 반환하고 기존 요청 내용을 유지한다")
    void rejectsDuplicateRequest() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.REVISION_REQUESTED, 0, "기존 요청");
        String url = imageUrl(OWNER_USER_ID, "00000000-0000-0000-0000-000000000001.png");

        request(body("\"" + MESSAGE + "\"", "[\"" + url + "\"]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_409_REVIEWED"));
        assertThat(submission.getReviewComment()).isEqualTo("기존 요청");
        verifyNoInteractions(imageStorageClient, eventPublisher);
        verify(jobRepository, never()).findByIdAndOwnerProfileId(any(), any());
    }

    @Test
    @DisplayName("사전 확인을 통과한 뒤 다른 요청이 먼저 처리됐으면 의뢰를 잠근 뒤 다시 확인해 409로 거부하고 기존 요청 내용을 유지한다")
    void recheckAfterLockRejectsConcurrentRequest() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.REVISION_REQUESTED, 0, "먼저 처리된 요청");
        givenTargetSubmission(81L, 42L, 0, JobSubmissionReviewStatus.PENDING);

        request(body("\"" + MESSAGE + "\"", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_409_REVIEWED"));
        assertThat(submission.getReviewComment()).isEqualTo("먼저 처리된 요청");
        verify(jobRepository).findByIdAndOwnerProfileId(42L, 5L);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("수정 가능 횟수를 모두 사용했으면 409 JOB_SUBMISSION_409_REVISION_LIMIT을 반환하고 제출물을 바꾸지 않는다")
    void rejectsWhenRevisionLimitReached() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.PENDING, 2, null);

        request(body("\"" + MESSAGE + "\"", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_409_REVISION_LIMIT"));
        assertUnchanged(submission);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("다른 의뢰의 제출물이면 404 JOB_SUBMISSION_404를 반환하고 제출물을 바꾸지 않는다")
    void rejectsSubmissionOfOtherJob() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.PENDING);
        givenTargetSubmission(81L, 43L, 0, JobSubmissionReviewStatus.PENDING);

        request(body("\"" + MESSAGE + "\"", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_404"));
        assertUnchanged(submission);
    }

    @Test
    @DisplayName("다른 사업주의 의뢰이면 404 JOB_404를 반환한다")
    void rejectsOtherOwnersJob() throws Exception {
        givenActiveOwner();

        request(body("\"" + MESSAGE + "\"", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSubmissionRepository);
    }

    @Test
    @DisplayName("존재하지 않는 제출물이면 404 JOB_SUBMISSION_404를 반환한다")
    void rejectsMissingSubmission() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.MATCHED);

        request(body("\"" + MESSAGE + "\"", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_404"));
    }

    @Test
    @DisplayName("진행 중이 아닌 의뢰이면 409 JOB_SUBMISSION_409_REVIEW_STATUS를 반환하고 제출물을 바꾸지 않는다")
    void rejectsClosedJob() throws Exception {
        givenActiveOwner();
        givenOwnedJob(JobStatus.CLOSED);
        JobSubmission submission = givenSubmission(JobSubmissionReviewStatus.PENDING);

        request(body("\"" + MESSAGE + "\"", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_409_REVIEW_STATUS"));
        assertUnchanged(submission);
    }

    @Test
    @DisplayName("사업주 프로필이 없는 사용자(학생 포함)는 403 OWNER_403으로 거부하고 의뢰를 조회하지 않는다")
    void rejectsUserWithoutOwnerProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.STUDENT).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.empty());

        request(body("\"" + MESSAGE + "\"", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OWNER_403"));
        verifyNoInteractions(jobRepository, jobSubmissionRepository, imageStorageClient);
    }

    @Test
    @DisplayName("의뢰 ID나 제출물 ID가 0 이하이면 400을 반환하고 조회하지 않는다")
    void rejectsNonPositiveIds() throws Exception {
        String body = body("\"" + MESSAGE + "\"", null);

        mockMvc.perform(post("/jobs/42/submissions/0/revision-request").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        mockMvc.perform(post("/jobs/0/submissions/81/revision-request").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userRepository, jobRepository, jobSubmissionRepository);
    }

    private ResultActions request(String body) throws Exception {
        return mockMvc.perform(post(URL).principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** messageJson과 imagesJson은 JSON 조각이고, imagesJson이 null이면 referenceImageUrls를 생략한다. */
    private String body(String messageJson, String imagesJson) {
        return imagesJson == null
                ? "{\"message\": " + messageJson + "}"
                : "{\"message\": " + messageJson + ", \"referenceImageUrls\": " + imagesJson + "}";
    }

    private void assertUnchanged(JobSubmission submission) {
        assertThat(submission.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
        assertThat(submission.getReviewComment()).isNull();
        assertThat(submission.getRevisionReferenceImageUrls()).isEmpty();
        assertThat(submission.getReviewedAt()).isNull();
    }

    private void givenActiveOwner() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.of(
                Owner.builder().id(5L).userId(OWNER_USER_ID).storeName("가꿈 카페").build()));
    }

    /** 잠금 전 사전 확인(프로젝션)과 잠금 후 조회(엔티티)가 같은 의뢰를 보게 한다. */
    private void givenOwnedJob(JobStatus status) {
        RevisionRequestTargetProjection target = mock(RevisionRequestTargetProjection.class);
        when(target.getStatus()).thenReturn(status);
        when(target.getRevisionCount()).thenReturn(2);
        when(jobRepository.findRevisionRequestTargetByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(target));
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .title("메뉴판 디자인")
                .status(status)
                .selectedStudentProfileId(7L)
                .revisionCount(2)
                .build()));
    }

    private JobSubmission givenSubmission(JobSubmissionReviewStatus reviewStatus) {
        return givenSubmission(reviewStatus, 0, null);
    }

    /** 잠금 전 사전 확인(프로젝션)과 잠금 후 조회(엔티티)가 같은 제출물을 보게 한다. */
    private JobSubmission givenSubmission(
            JobSubmissionReviewStatus reviewStatus, int revisionNumber, String reviewComment) {
        JobSubmission submission = JobSubmission.builder()
                .id(81L)
                .jobId(42L)
                .submissionType(revisionNumber == 0 ? JobSubmissionType.DRAFT : JobSubmissionType.REVISION)
                .revisionNumber(revisionNumber)
                .fileUrls(List.of("https://example.com/draft.pdf"))
                .message("초안입니다.")
                .reviewStatus(reviewStatus)
                .reviewComment(reviewComment)
                .build();
        when(jobSubmissionRepository.findById(81L)).thenReturn(Optional.of(submission));
        givenTargetSubmission(81L, 42L, revisionNumber, reviewStatus);
        return submission;
    }

    private void givenTargetSubmission(
            Long id, Long jobId, int revisionNumber, JobSubmissionReviewStatus reviewStatus) {
        ReviewTargetProjection target = mock(ReviewTargetProjection.class);
        when(target.getJobId()).thenReturn(jobId);
        when(target.getReviewStatus()).thenReturn(reviewStatus);
        when(target.getRevisionNumber()).thenReturn(revisionNumber);
        when(jobSubmissionRepository.findReviewTargetById(id)).thenReturn(Optional.of(target));
    }

    private String givenUploadedImage(String fileName) {
        String url = imageUrl(OWNER_USER_ID, fileName);
        when(imageStorageClient.findKey(url, KEY_PREFIX)).thenReturn(Optional.of(keyOf(url)));
        when(imageStorageClient.exists(keyOf(url))).thenReturn(true);
        return url;
    }

    private String imageUrl(String userId, String fileName) {
        return "https://images.example.com/images/job/" + userId + "/" + fileName;
    }

    private String keyOf(String url) {
        return url.substring("https://images.example.com/".length());
    }
}
