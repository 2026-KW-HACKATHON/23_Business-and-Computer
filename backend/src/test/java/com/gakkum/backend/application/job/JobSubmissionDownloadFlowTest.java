package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
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
import com.gakkum.backend.global.transaction.ImmediateTransactionTemplate;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@DisplayName("여러 의뢰의 선택 작업물 ZIP 다운로드 전체 흐름 (POST /jobs/submissions/download)")
class JobSubmissionDownloadFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB6D";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB6E";
    private static final String URL = "/jobs/submissions/download";
    private static final String BUCKET = "gakkum-images-test";
    private static final String BASE_URL = "https://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/";
    private static final String FOLDER = "0b4f2a3e-6a8c-4a39-9f55-8f1d8f0b2c1";
    // 의뢰 42와 43은 사장님 프로필 5의 의뢰이고 담당 학생은 프로필 7이다
    private static final String POSTER_DRAFT_KEY = "job-submissions/42/7/" + FOLDER + "1/시안.png";
    private static final String POSTER_REVISION_KEY = "job-submissions/42/7/" + FOLDER + "2/시안.png";
    private static final String POSTER_UNSELECTED_KEY = "job-submissions/42/7/" + FOLDER + "3/고르지 않은 파일.pdf";
    private static final String MENU_KEY = "job-submissions/43/7/" + FOLDER + "4/메뉴판 문서.pdf";
    private static final String SAMPLE_URL = "https://placehold.co/600x400.png";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final S3Client s3Client = mock(S3Client.class);
    private final Map<String, byte[]> objects = new HashMap<>();
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(s3Client.utilities()).thenReturn(S3Utilities.builder().region(Region.AP_NORTHEAST_2).build());
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            HeadObjectRequest request = invocation.getArgument(0);
            if (!objects.containsKey(request.key())) {
                throw NoSuchKeyException.builder().statusCode(404).build();
            }
            return HeadObjectResponse.builder().contentLength((long) objects.get(request.key()).length).build();
        });
        when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            return new ResponseInputStream<>(GetObjectResponse.builder().build(),
                    AbortableInputStream.create(new ByteArrayInputStream(objects.get(request.key()))));
        });
        objects.put(POSTER_DRAFT_KEY, "포스터 초안".getBytes(StandardCharsets.UTF_8));
        objects.put(POSTER_REVISION_KEY, "포스터 수정안".getBytes(StandardCharsets.UTF_8));
        objects.put(POSTER_UNSELECTED_KEY, "고르지 않음".getBytes(StandardCharsets.UTF_8));
        objects.put(MENU_KEY, "메뉴판".getBytes(StandardCharsets.UTF_8));

        JobSubmissionFileStorageClient storageClient = new JobSubmissionFileStorageClient(
                s3Client, mock(S3Presigner.class), BUCKET, Duration.ofMinutes(10), 1);
        UserService userService = new UserService(userRepository, mock(JwtService.class));
        JobService jobService = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
                mock(JobApplicationRepository.class), jobSubmissionRepository,
                Clock.systemUTC());
        JobFacade facade = new JobFacade(userService, new OwnerService(ownerRepository), jobService,
                mock(SpecialtyCategoryService.class), mock(SpecialtyService.class), new StudentService(studentRepository),
                storageClient, mock(ChatAttachmentPolicy.class), mock(PaymentService.class),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class), mock(ApplicationEventPublisher.class),
                new ImmediateTransactionTemplate(), mock(ChatRoomService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = { "OWNER", "STUDENT" })
    @DisplayName("사장님과 담당 학생은 여러 의뢰에서 고른 파일만 의뢰 폴더와 순번을 붙인 이름으로 담긴 ZIP을 받고 내용은 원본과 같다")
    void downloadsSelectedFilesOfSeveralJobs(UserRole role) throws Exception {
        givenViewer(role);
        givenJobs(job(42L, JobStatus.CLOSED, 7L), job(43L, JobStatus.CANCELLED, 7L));
        givenSubmissions(
                submission(42L, 0, url(POSTER_DRAFT_KEY), url(POSTER_UNSELECTED_KEY)),
                submission(42L, 1, url(POSTER_REVISION_KEY)),
                submission(43L, 0, url(MENU_KEY)));

        MvcResult result = download(body(
                files(42L, url(POSTER_REVISION_KEY), url(POSTER_DRAFT_KEY)), files(43L, url(MENU_KEY))))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"work-submissions.zip\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        Map<String, String> entries = unzip(result.getResponse().getContentAsByteArray());
        assertThat(entries).containsExactly(
                Map.entry("job-42/1_시안.png", "포스터 수정안"),
                Map.entry("job-42/2_시안.png", "포스터 초안"),
                Map.entry("job-43/1_메뉴판 문서.pdf", "메뉴판"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"jobs\":[]}",
            "{\"jobs\":[null]}",
            "{\"jobs\":[{\"fileUrls\":[\"https://example.com/a.png\"]}]}",
            "{\"jobs\":[{\"jobId\":0,\"fileUrls\":[\"https://example.com/a.png\"]}]}",
            "{\"jobs\":[{\"jobId\":-1,\"fileUrls\":[\"https://example.com/a.png\"]}]}",
            "{\"jobs\":[{\"jobId\":\"abc\",\"fileUrls\":[\"https://example.com/a.png\"]}]}",
            "{\"jobs\":[{\"jobId\":42}]}",
            "{\"jobs\":[{\"jobId\":42,\"fileUrls\":[]}]}",
            "{\"jobs\":[{\"jobId\":42,\"fileUrls\":[\" \"]}]}",
            "{\"jobs\":[{\"jobId\":42,\"fileUrls\":[\"https://example.com/a.png\",\"https://example.com/a.png\"]}]}",
            "{\"jobs\":[{\"jobId\":42,\"fileUrls\":[\"https://example.com/a.png\"]},"
                    + "{\"jobId\":42,\"fileUrls\":[\"https://example.com/b.png\"]}]}" })
    @DisplayName("빈 목록, 잘못된 의뢰 ID, 빈 파일 목록, 중복된 의뢰·URL은 400 COMMON_400으로 거부하고 조회하지 않는다")
    void rejectsInvalidRequest(String body) throws Exception {
        download(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(userRepository, jobRepository, jobSubmissionRepository, s3Client);
    }

    @Test
    @DisplayName("다른 사장님의 의뢰가 하나라도 섞이면 전체를 404 JOB_404로 거부하고 제출물과 저장소를 조회하지 않는다")
    void rejectsWhenAnyJobBelongsToAnotherOwner() throws Exception {
        givenViewer(UserRole.OWNER);
        givenJobs(job(42L, JobStatus.MATCHED, 7L),
                Job.builder().id(43L).ownerProfileId(6L).status(JobStatus.MATCHED).selectedStudentProfileId(7L).build());

        download(body(files(42L, url(POSTER_DRAFT_KEY)), files(43L, url(MENU_KEY))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSubmissionRepository, s3Client);
    }

    @Test
    @DisplayName("다른 학생이 담당한 의뢰는 404 JOB_404로 거부한다")
    void rejectsJobOfAnotherStudent() throws Exception {
        givenViewer(UserRole.STUDENT);
        givenJobs(job(43L, JobStatus.MATCHED, 8L));

        download(body(files(43L, url(MENU_KEY))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSubmissionRepository, s3Client);
    }

    @Test
    @DisplayName("존재하지 않는 의뢰가 섞이면 404 JOB_404로 거부한다")
    void rejectsMissingJob() throws Exception {
        givenViewer(UserRole.OWNER);
        givenJobs(job(42L, JobStatus.MATCHED, 7L));

        download(body(files(42L, url(POSTER_DRAFT_KEY)), files(99L, url(MENU_KEY))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSubmissionRepository, s3Client);
    }

    @Test
    @DisplayName("본인 의뢰여도 그 의뢰의 제출물에 등록되지 않은 URL은 400 JOB_SUBMISSION_400_FILE_URL로 거부하고 저장소를 조회하지 않는다")
    void rejectsUrlNotRegisteredInSubmissions() throws Exception {
        givenViewer(UserRole.OWNER);
        givenJobs(job(42L, JobStatus.MATCHED, 7L), job(43L, JobStatus.MATCHED, 7L));
        givenSubmissions(submission(42L, 0, url(POSTER_DRAFT_KEY)), submission(43L, 0, url(MENU_KEY)));

        // 저장소에는 있지만 제출하지 않은 파일, 다른 의뢰에 제출한 파일
        for (String fileUrl : List.of(url(POSTER_REVISION_KEY), url(MENU_KEY))) {
            download(body(files(42L, url(POSTER_DRAFT_KEY), fileUrl)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_400_FILE_URL"));
        }
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
        verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("제출물에 등록돼 있어도 서비스 저장소 주소가 아닌 외부 URL은 400 JOB_SUBMISSION_400_FILE_URL로 거부하고 호출하지 않는다")
    void rejectsExternalUrlEvenIfRegistered() throws Exception {
        givenViewer(UserRole.OWNER);
        givenJobs(job(42L, JobStatus.MATCHED, 7L));
        givenSubmissions(submission(42L, 0, SAMPLE_URL));

        download(body(files(42L, SAMPLE_URL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_400_FILE_URL"));
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
        verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("저장소에 없는 파일이 있으면 404 JOB_SUBMISSION_404_FILE로 거부하고, 슬롯을 돌려줘 다음 요청은 받는다")
    void rejectsMissingStorageObjectAndReleasesSlot() throws Exception {
        givenViewer(UserRole.OWNER);
        givenJobs(job(43L, JobStatus.MATCHED, 7L));
        givenSubmissions(submission(43L, 0, url(MENU_KEY)));
        byte[] content = objects.remove(MENU_KEY);

        download(body(files(43L, url(MENU_KEY))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_404_FILE"));
        verify(s3Client, never()).getObject(any(GetObjectRequest.class));

        objects.put(MENU_KEY, content);
        download(body(files(43L, url(MENU_KEY)))).andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("저장소에 연결하지 못하면 502 JOB_SUBMISSION_502로 거부하고, 슬롯을 돌려줘 다음 요청은 받는다")
    void rejectsWhenStorageIsUnavailableAndReleasesSlot() throws Exception {
        givenViewer(UserRole.OWNER);
        givenJobs(job(43L, JobStatus.MATCHED, 7L));
        givenSubmissions(submission(43L, 0, url(MENU_KEY)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(SdkClientException.create("timeout"))
                .thenReturn(HeadObjectResponse.builder().contentLength(1L).build());

        download(body(files(43L, url(MENU_KEY))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_502"));

        download(body(files(43L, url(MENU_KEY)))).andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("동시 처리 한도만큼 전송 중이면 503 JOB_SUBMISSION_503_DOWNLOAD로 거부하고, 전송이 끝나면 다시 받는다")
    void rejectsOverConcurrencyLimitUntilTransferEnds() throws Exception {
        givenViewer(UserRole.OWNER);
        givenJobs(job(43L, JobStatus.MATCHED, 7L));
        givenSubmissions(submission(43L, 0, url(MENU_KEY)));
        CountDownLatchStream gate = new CountDownLatchStream(objects.get(MENU_KEY));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> new ResponseInputStream<>(
                GetObjectResponse.builder().build(), AbortableInputStream.create(gate)));

        MvcResult inProgress = download(body(files(43L, url(MENU_KEY))))
                .andExpect(request().asyncStarted())
                .andReturn();
        download(body(files(43L, url(MENU_KEY))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_503_DOWNLOAD"));

        gate.open();
        mockMvc.perform(asyncDispatch(inProgress)).andExpect(status().isOk());
        download(body(files(43L, url(MENU_KEY)))).andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("사장님·학생이 아닌 역할은 403 JOB_SUBMISSION_403_VIEW로 거부하고 의뢰를 조회하지 않는다")
    void rejectsDisallowedRole() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.PENDING).build()));

        download(body(files(43L, url(MENU_KEY))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_403_VIEW"));
        verifyNoInteractions(ownerRepository, studentRepository, jobRepository, jobSubmissionRepository, s3Client);
    }

    @Test
    @DisplayName("사장님 역할이어도 사장님 프로필이 없으면 403 JOB_SUBMISSION_403_VIEW로 거부한다")
    void rejectsOwnerWithoutProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.empty());

        download(body(files(43L, url(MENU_KEY))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_403_VIEW"));
        verifyNoInteractions(jobRepository, jobSubmissionRepository, s3Client);
    }

    private ResultActions download(String body) throws Exception {
        return mockMvc.perform(post(URL).principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 의뢰 42·43의 당사자(사장님 프로필 5, 담당 학생 프로필 7)로 로그인한다. */
    private void givenViewer(UserRole role) {
        if (role == UserRole.OWNER) {
            when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                    User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build()));
            when(ownerRepository.findByUserId(OWNER_USER_ID))
                    .thenReturn(Optional.of(Owner.builder().id(5L).userId(OWNER_USER_ID).build()));
            return;
        }
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID))
                .thenReturn(Optional.of(Student.builder().id(7L).userId(STUDENT_USER_ID).build()));
    }

    private void givenJobs(Job... jobs) {
        when(jobRepository.findAllById(any())).thenAnswer(invocation -> {
            List<Long> jobIds = invocation.getArgument(0);
            return List.of(jobs).stream().filter(job -> jobIds.contains(job.getId())).toList();
        });
    }

    private void givenSubmissions(JobSubmission... submissions) {
        when(jobSubmissionRepository.findByJobIdIn(any())).thenAnswer(invocation -> {
            List<Long> jobIds = invocation.getArgument(0);
            return List.of(submissions).stream()
                    .filter(submission -> jobIds.contains(submission.getJobId())).toList();
        });
    }

    private static Job job(Long jobId, JobStatus status, Long selectedStudentProfileId) {
        return Job.builder()
                .id(jobId)
                .ownerProfileId(5L)
                .status(status)
                .selectedStudentProfileId(selectedStudentProfileId)
                .build();
    }

    private static JobSubmission submission(Long jobId, int revisionNumber, String... fileUrls) {
        return JobSubmission.builder()
                .jobId(jobId)
                .revisionNumber(revisionNumber)
                .fileUrls(List.of(fileUrls))
                .build();
    }

    private static String url(String key) {
        return S3Utilities.builder().region(Region.AP_NORTHEAST_2).build()
                .getUrl(url -> url.bucket(BUCKET).key(key)).toString();
    }

    private static String body(String... jobs) {
        return "{\"jobs\":[" + String.join(",", jobs) + "]}";
    }

    private static String files(Long jobId, String... fileUrls) {
        return "{\"jobId\":" + jobId + ",\"fileUrls\":[\"" + String.join("\",\"", fileUrls) + "\"]}";
    }

    private static Map<String, String> unzip(byte[] zip) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                entries.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    /** 열어 줄 때까지 읽기를 붙잡아 전송이 진행 중인 상태를 만든다. */
    private static final class CountDownLatchStream extends ByteArrayInputStream {

        private final java.util.concurrent.CountDownLatch opened = new java.util.concurrent.CountDownLatch(1);

        private CountDownLatchStream(byte[] content) {
            super(content);
        }

        private void open() {
            opened.countDown();
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length) {
            try {
                opened.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return super.read(buffer, offset, length);
        }
    }
}
