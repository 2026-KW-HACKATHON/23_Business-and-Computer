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

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.media.client.MediaImageStorageClient;
import org.springframework.util.unit.DataSize;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.StudentSpecialtyRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("의뢰 생성 전체 흐름 (POST /jobs)")
class JobCreationFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";

    private static final String REQUEST_BODY = """
            {
              "specialtyIds": [1, 2, 3],
              "title": "의뢰 제목",
              "description": "맡기고 싶은 일",
              "budget": 500000,
              "draftDeadline": "2999-01-01",
              "finalDeadline": "2999-01-15",
              "revisionCount": 1
            }
            """;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final SpecialtyCategoryRepository specialtyCategoryRepository = mock(SpecialtyCategoryRepository.class);
    private final StudentSpecialtyRepository studentSpecialtyRepository = mock(StudentSpecialtyRepository.class);
    private final MediaImageStorageClient imageStorageClient = mock(MediaImageStorageClient.class);
    private final JwtService jwtService = mock(JwtService.class);

    private final User ownerUser = User.builder()
            .id(USER_ID)
            .username(USERNAME)
            .isLock(false)
            .role(UserRole.OWNER)
            .build();
    private final Owner ownerProfile = Owner.builder()
            .id(5L)
            .userId(USER_ID)
            .businessNumber("1234567890")
            .storeName("치킨플러스")
            .categoryId(2L)
            .storeImageUrls(List.of())
            .build();
    private final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            USERNAME,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_OWNER")));

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, jwtService);
        OwnerService ownerService = new OwnerService(ownerRepository);
        SpecialtyService specialtyService = new SpecialtyService(specialtyRepository, studentSpecialtyRepository);
        JobService jobService = new JobService(jobRepository, jobSpecialtyRepository,
                jobApplicationRepository, mock(JobSubmissionRepository.class), Clock.systemUTC());
        SpecialtyCategoryService specialtyCategoryService =
                new SpecialtyCategoryService(specialtyCategoryRepository, specialtyRepository);
        JobFacade facade = new JobFacade(userService, ownerService, jobService,
                specialtyCategoryService, specialtyService, mock(StudentService.class),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class), mock(PaymentService.class),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), new MediaService(imageStorageClient, DataSize.ofMegabytes(10)), mock(ApplicationEventPublisher.class));
        JobController controller = new JobController(facade);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("정상 요청이면 의뢰와 특기가 저장되고 data 필드 없이 성공 응답을 반환한다")
    void createsJobThroughControllerFacadeAndDomainServices() throws Exception {
        givenOwner();
        when(specialtyRepository.countByIdIn(List.of(1L, 2L, 3L))).thenReturn(3L);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            return Job.builder()
                    .id(100L)
                    .ownerProfileId(job.getOwnerProfileId())
                    .title(job.getTitle())
                    .description(job.getDescription())
                    .budget(job.getBudget())
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .revisionCount(job.getRevisionCount())
                    .status(job.getStatus())
                    .build();
        });

        createJob(REQUEST_BODY)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.error").doesNotExist());

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        Job job = jobCaptor.getValue();
        assertThat(job.getOwnerProfileId()).isEqualTo(ownerProfile.getId());
        assertThat(job.getTitle()).isEqualTo("의뢰 제목");
        assertThat(job.getDescription()).isEqualTo("맡기고 싶은 일");
        assertThat(job.getBudget()).isEqualTo(500000L);
        assertThat(job.getRevisionCount()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(job.getReferenceImageUrls()).isEmpty();
        verifyNoInteractions(imageStorageClient);

        ArgumentCaptor<List<JobSpecialty>> specialtiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(jobSpecialtyRepository).saveAll(specialtiesCaptor.capture());
        assertThat(specialtiesCaptor.getValue())
                .extracting(JobSpecialty::getJobId, JobSpecialty::getSpecialtyId)
                .containsExactly(
                        Tuple.tuple(100L, 1L),
                        Tuple.tuple(100L, 2L),
                        Tuple.tuple(100L, 3L));
    }

    @Test
    @DisplayName("존재하지 않거나 잠긴 사용자는 401을 반환하고 아무것도 저장하지 않는다")
    void rejectsUnknownOrLockedUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        createJob(REQUEST_BODY)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(ownerRepository, jobRepository, jobSpecialtyRepository, specialtyRepository);
    }

    @Test
    @DisplayName("사장님 프로필이 없는 사용자는 403을 반환하고 아무것도 저장하지 않는다")
    void rejectsUserWithoutOwnerProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(ownerUser));
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        createJob(REQUEST_BODY)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("OWNER_403"));

        verifyNoInteractions(jobRepository, jobSpecialtyRepository, specialtyRepository);
    }

    @Test
    @DisplayName("존재하지 않는 특기가 포함되면 400을 반환하고 저장하지 않는다")
    void rejectsUnknownSpecialty() throws Exception {
        givenOwner();
        when(specialtyRepository.countByIdIn(List.of(1L, 2L, 3L))).thenReturn(2L);

        createJob(REQUEST_BODY)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("SPECIALTY_400"));

        verify(jobRepository, never()).save(any());
        verifyNoInteractions(jobSpecialtyRepository);
    }

    @Test
    @DisplayName("필수값이 없으면 400을 반환하고 인증 사용자를 조회하지 않는다")
    void rejectsMissingRequiredFields() throws Exception {
        createJob("{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, ownerRepository, jobRepository, jobSpecialtyRepository);
    }

    @Test
    @DisplayName("선지급 마감일이 최종 마감일보다 늦으면 400을 반환한다")
    void rejectsDraftDeadlineAfterFinalDeadline() throws Exception {
        createJob(REQUEST_BODY
                .replace("\"draftDeadline\": \"2999-01-01\"", "\"draftDeadline\": \"2999-01-20\""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, ownerRepository, jobRepository, jobSpecialtyRepository);
    }

    @Test
    @DisplayName("수정 횟수가 음수이면 400을 반환한다")
    void rejectsNegativeRevisionCount() throws Exception {
        createJob(REQUEST_BODY.replace("\"revisionCount\": 1", "\"revisionCount\": -1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, ownerRepository, jobRepository, jobSpecialtyRepository);
    }

    @Test
    @DisplayName("본인이 업로드한 참고 사진은 최대 4장까지 요청 순서대로 저장한다")
    void storesMultipleUploadedReferenceImages() throws Exception {
        givenValidCreation();
        String first = jobImageUrl(USER_ID, "00000000-0000-0000-0000-000000000002.png");
        String second = jobImageUrl(USER_ID, "00000000-0000-0000-0000-000000000001.png");
        String third = jobImageUrl(USER_ID, "00000000-0000-0000-0000-000000000004.png");
        String fourth = jobImageUrl(USER_ID, "00000000-0000-0000-0000-000000000003.png");
        givenUploadedImage(first);
        givenUploadedImage(second);
        givenUploadedImage(third);
        givenUploadedImage(fourth);

        createJob(withImages("[\"" + first + "\",\"" + second + "\",\"" + third + "\",\"" + fourth + "\"]"))
                .andExpect(status().isOk());

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getReferenceImageUrls()).containsExactly(first, second, third, fourth);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 1000})
    @DisplayName("참고 사진이 4장을 초과하면 저장소 조회와 저장 전에 400으로 거부한다")
    void rejectsTooManyReferenceImages(int count) throws Exception {
        String images = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> "\"" + jobImageUrl(USER_ID,
                        String.format("00000000-0000-0000-0000-%012d.png", index)) + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));

        createJob(withImages(images)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, ownerRepository, jobRepository, jobSpecialtyRepository, imageStorageClient);
    }

    @Test
    @DisplayName("4장 이내여도 같은 참고 사진 URL이 중복되면 저장소 조회와 저장 전에 400으로 거부한다")
    void rejectsDuplicateReferenceImages() throws Exception {
        String url = jobImageUrl(USER_ID, "00000000-0000-0000-0000-000000000001.png");

        createJob(withImages("[\"" + url + "\",\"" + url + "\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, ownerRepository, jobRepository, jobSpecialtyRepository, imageStorageClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]"})
    @DisplayName("참고 사진이 null 또는 빈 배열이면 사진 없이 등록한다")
    void acceptsNoReferenceImages(String images) throws Exception {
        givenValidCreation();
        createJob(withImages(images)).andExpect(status().isOk());

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getReferenceImageUrls()).isEmpty();
        verifyNoInteractions(imageStorageClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[null]", "[\"\"]", "[\"   \"]"})
    @DisplayName("참고 사진 배열에 null이나 빈 문자열·공백이 있으면 저장 전에 400으로 거부한다")
    void rejectsBlankImageElements(String images) throws Exception {
        createJob(withImages(images)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository, imageStorageClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example.com/image.png",
            "https://images.example.com/images/job/other-user/00000000-0000-0000-0000-000000000001.png",
            "https://images.example.com/images/proposal/01K58M6PJV8VAJMXHBHJ2PNB5C/00000000-0000-0000-0000-000000000001.png",
            "https://images.example.com/images/job/01K58M6PJV8VAJMXHBHJ2PNB5C/photo.png"})
    @DisplayName("외부·타인·다른 용도·발급 형태가 아닌 사진 URL은 400으로 거부하고 업로드 여부를 조회하지 않는다")
    void rejectsUnissuedReferenceImageUrl(String invalidUrl) throws Exception {
        givenValidCreation();
        String validUrl = jobImageUrl(USER_ID, "00000000-0000-0000-0000-000000000001.png");
        givenUploadedImage(validUrl);
        when(imageStorageClient.findKey(invalidUrl, "images/job/" + USER_ID + "/"))
                .thenReturn(invalidUrl.endsWith("/photo.png")
                        ? Optional.of("images/job/" + USER_ID + "/photo.png") : Optional.empty());

        createJob(withImages("[\"" + validUrl + "\",\"" + invalidUrl + "\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("JOB_400_IMAGE_URL"));
        verify(imageStorageClient, never()).exists(any());
        verify(jobRepository, never()).save(any());
        verifyNoInteractions(jobSpecialtyRepository);
    }

    @Test
    @DisplayName("발급된 사진 URL이라도 업로드가 끝나지 않았으면 409로 거부하고 의뢰와 특기를 저장하지 않는다")
    void rejectsImageNotUploaded() throws Exception {
        givenValidCreation();
        String url = jobImageUrl(USER_ID, "00000000-0000-0000-0000-000000000001.png");
        String key = url.substring("https://images.example.com/".length());
        when(imageStorageClient.findKey(url, "images/job/" + USER_ID + "/")).thenReturn(Optional.of(key));

        createJob(withImages("[\"" + url + "\"]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_409_IMAGE_NOT_UPLOADED"));
        verify(jobRepository, never()).save(any());
        verifyNoInteractions(jobSpecialtyRepository);
    }

    private void givenValidCreation() {
        givenOwner();
        when(specialtyRepository.countByIdIn(List.of(1L, 2L, 3L))).thenReturn(3L);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenUploadedImage(String url) {
        String key = url.substring("https://images.example.com/".length());
        when(imageStorageClient.findKey(url, "images/job/" + USER_ID + "/")).thenReturn(Optional.of(key));
        when(imageStorageClient.exists(key)).thenReturn(true);
    }

    private String jobImageUrl(String userId, String fileName) {
        return "https://images.example.com/images/job/" + userId + "/" + fileName;
    }

    private String withImages(String images) {
        return REQUEST_BODY.replace("\"revisionCount\": 1", "\"revisionCount\": 1, \"referenceImageUrls\": " + images);
    }

    private ResultActions createJob(String body) throws Exception {
        return mockMvc.perform(post("/jobs")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void givenOwner() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(ownerUser));
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(ownerProfile));
    }
}
