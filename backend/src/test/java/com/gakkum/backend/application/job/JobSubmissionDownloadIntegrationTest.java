package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.util.JWTUtil;
import com.zaxxer.hikari.HikariDataSource;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.Abortable;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * ZIP 다운로드는 응답을 비동기로 마무리하므로 MockMvc로는 드러나지 않는 동작이 있다.
 * 실제 서버와 Security 필터, PostgreSQL로 띄우고 저장소(S3)만 가짜로 바꿔, 전송이 끝날 때까지의 인증·CORS와
 * 전송 중 실패·취소·타임아웃에서 응답이 어떻게 끝나고 처리 슬롯과 저장소 스트림이 돌아오는지 확인한다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "job-submission-file.download-max-concurrency=2",
        "spring.mvc.async.request-timeout=3s" })
@ActiveProfiles("local")
class JobSubmissionDownloadIntegrationTest {

    private static final String ORIGIN = "http://localhost:5173";
    private static final int MAX_CONCURRENCY = 2;
    // 응답 버퍼보다 커서 다음 파일을 읽기 전에 전송이 시작되는, 압축되지 않는 내용
    private static final byte[] LARGE_CONTENT = randomBytes(256 * 1024);

    @MockitoBean
    private S3Client s3Client;

    @Value("${local.server.port}")
    private int port;
    @Value("${s3.image-bucket}")
    private String bucket;
    @Value("${s3.region}")
    private String region;

    @Autowired
    private JWTUtil jwtUtil;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private JobSubmissionRepository jobSubmissionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OwnerRepository ownerRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final Map<String, Supplier<InputStream>> objects = new ConcurrentHashMap<>();
    private final AtomicInteger openedStreams = new AtomicInteger();
    private final AtomicInteger releasedStreams = new AtomicInteger();
    private final List<String> userIds = new ArrayList<>();
    private final List<Long> ownerProfileIds = new ArrayList<>();
    private final List<Long> studentProfileIds = new ArrayList<>();
    private final List<Long> jobIds = new ArrayList<>();
    private final List<Long> submissionIds = new ArrayList<>();

    private S3Utilities s3Utilities;
    private String ownerToken;
    private String studentToken;
    private String otherOwnerToken;
    private Long studentProfileId;
    private Long posterJobId;
    private Long menuJobId;
    private String posterDraftUrl;
    private String posterRevisionUrl;
    private String posterUnselectedUrl;
    private String menuUrl;

    @BeforeEach
    void setUp() {
        s3Utilities = S3Utilities.builder().region(Region.of(region)).build();
        when(s3Client.utilities()).thenReturn(s3Utilities);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            HeadObjectRequest request = invocation.getArgument(0);
            if (!objects.containsKey(request.key())) {
                throw NoSuchKeyException.builder().statusCode(404).build();
            }
            return HeadObjectResponse.builder().contentLength(1L).build();
        });
        when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            openedStreams.incrementAndGet();
            return new ResponseInputStream<>(GetObjectResponse.builder().build(),
                    AbortableInputStream.create(new ReleaseCountingStream(objects.get(request.key()).get())));
        });

        String unique = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Long ownerProfileId = saveOwner(unique.substring(0, 26), "TEST_ZIP_OWNER_" + unique, "ZIPA-" + unique);
        ownerToken = jwtUtil.createJWT("TEST_ZIP_OWNER_" + unique, UserRole.OWNER.name(), true);
        saveOwner(unique.substring(6), "TEST_ZIP_OTHER_" + unique, "ZIPB-" + unique);
        otherOwnerToken = jwtUtil.createJWT("TEST_ZIP_OTHER_" + unique, UserRole.OWNER.name(), true);

        String studentUserId = new StringBuilder(unique).reverse().substring(0, 26);
        userIds.add(studentUserId);
        userRepository.saveAndFlush(User.builder()
                .id(studentUserId).username("TEST_ZIP_STUDENT_" + unique).name("ZIP 테스트 학생")
                .isLock(false).role(UserRole.STUDENT).build());
        studentToken = jwtUtil.createJWT("TEST_ZIP_STUDENT_" + unique, UserRole.STUDENT.name(), true);
        studentProfileId = studentRepository.saveAndFlush(Student.create(
                studentUserId, "광운대학교", "TEST-" + unique, "소프트웨어학부", null, null, null)).getId();
        studentProfileIds.add(studentProfileId);

        posterJobId = saveMatchedJob(ownerProfileId, "포스터 의뢰");
        menuJobId = saveMatchedJob(ownerProfileId, "메뉴판 의뢰");
        posterDraftUrl = putObject(posterJobId, "시안.png", "포스터 초안".getBytes(StandardCharsets.UTF_8));
        posterRevisionUrl = putObject(posterJobId, "시안.png", LARGE_CONTENT);
        posterUnselectedUrl = putObject(posterJobId, "고르지 않은 파일.pdf", new byte[] { 1 });
        menuUrl = putObject(menuJobId, "메뉴판 문서.pdf", "메뉴판".getBytes(StandardCharsets.UTF_8));
        JobSubmission posterDraft = JobSubmission.create(posterJobId, JobSubmissionType.DRAFT, 0,
                List.of(posterDraftUrl, posterUnselectedUrl), "초안입니다.");
        posterDraft.requestRevision(LocalDateTime.now(), "글씨를 키워 주세요.", List.of());
        saveSubmission(posterDraft);
        saveSubmission(JobSubmission.create(posterJobId, JobSubmissionType.REVISION, 1,
                List.of(posterRevisionUrl), "수정안입니다."));
        saveSubmission(JobSubmission.create(menuJobId, JobSubmissionType.DRAFT, 0, List.of(menuUrl), "초안입니다."));
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            jobSubmissionRepository.deleteAllById(submissionIds);
            jobRepository.deleteAllById(jobIds);
            studentRepository.deleteAllById(studentProfileIds);
            ownerRepository.deleteAllById(ownerProfileIds);
            userRepository.deleteAllById(userIds);
        });
    }

    @Test
    @DisplayName("사장님이 여러 의뢰에서 고른 파일만 의뢰 폴더·순번·한글 파일명으로 담긴 ZIP을 끝까지 받고 브라우저가 파일명 헤더를 읽을 수 있다")
    void ownerDownloadsSelectedFilesAsZip() throws Exception {
        HttpResponse<byte[]> response = download(ownerToken, body(
                posterJobId, List.of(posterDraftUrl, posterRevisionUrl), menuJobId, List.of(menuUrl)));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/zip");
        assertThat(response.headers().firstValue("Content-Disposition"))
                .contains("attachment; filename=\"work-submissions.zip\"");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(ORIGIN);
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
        assertThat(response.headers().firstValue("Access-Control-Expose-Headers")).contains("Content-Disposition");

        Map<String, byte[]> entries = unzip(response.body());
        assertThat(entries.keySet()).containsExactly(
                "job-" + posterJobId + "/1_시안.png",
                "job-" + posterJobId + "/2_시안.png",
                "job-" + menuJobId + "/1_메뉴판 문서.pdf");
        assertThat(entries.get("job-" + posterJobId + "/1_시안.png"))
                .isEqualTo("포스터 초안".getBytes(StandardCharsets.UTF_8));
        assertThat(entries.get("job-" + posterJobId + "/2_시안.png")).isEqualTo(LARGE_CONTENT);
        assertThat(entries.get("job-" + menuJobId + "/1_메뉴판 문서.pdf"))
                .isEqualTo("메뉴판".getBytes(StandardCharsets.UTF_8));
        assertStreamsReleased();
    }

    @Test
    @DisplayName("담당 학생도 본인 의뢰의 파일을 ZIP으로 받는다")
    void studentDownloadsOwnSubmissionFiles() throws Exception {
        HttpResponse<byte[]> response = download(studentToken, body(menuJobId, List.of(menuUrl)));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(unzip(response.body()).keySet()).containsExactly("job-" + menuJobId + "/1_메뉴판 문서.pdf");
    }

    @Test
    @DisplayName("토큰이 없으면 401로 거부하고 저장소를 읽지 않는다")
    void rejectsUnauthenticatedRequest() throws Exception {
        HttpResponse<byte[]> response = download(null, body(menuJobId, List.of(menuUrl)));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(text(response)).contains("COMMON_401");
        verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("다른 사장님의 의뢰가 하나라도 섞이면 전체를 JOB_404로 거부하고 ApiResponse 형식으로 응답한다")
    void rejectsOtherOwnersJob() throws Exception {
        HttpResponse<byte[]> response = download(otherOwnerToken, body(menuJobId, List.of(menuUrl)));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                contentType -> assertThat(contentType).startsWith("application/json"));
        assertThat(response.headers().firstValue("Content-Disposition")).isEmpty();
        assertThat(text(response)).contains("\"success\":false").contains("JOB_404");
        verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("저장소에 없는 파일이 하나라도 있으면 전송 전에 JOB_SUBMISSION_404_FILE로 거부하고 처리 슬롯을 돌려준다")
    void rejectsMissingStorageObjectBeforeSending() throws Exception {
        objects.remove(key(menuUrl));

        for (int attempt = 0; attempt <= MAX_CONCURRENCY; attempt++) {
            HttpResponse<byte[]> response = download(ownerToken,
                    body(posterJobId, List.of(posterDraftUrl), menuJobId, List.of(menuUrl)));

            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(text(response)).contains("JOB_SUBMISSION_404_FILE");
        }
        verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("동시 처리 한도를 넘는 요청은 기다리지 않고 503으로 거부하고, 전송 중에는 DB 커넥션을 쥐지 않으며, 끝난 뒤에는 다시 받는다")
    void rejectsRequestsOverConcurrencyLimitWithoutWaiting() throws Exception {
        CountDownLatch reading = new CountDownLatch(MAX_CONCURRENCY);
        CountDownLatch resume = new CountDownLatch(1);
        objects.put(key(menuUrl), () -> new GatedStream("메뉴판".getBytes(StandardCharsets.UTF_8), reading, resume));
        String body = body(menuJobId, List.of(menuUrl));

        List<CompletableFuture<HttpResponse<byte[]>>> inProgress = new ArrayList<>();
        for (int index = 0; index < MAX_CONCURRENCY; index++) {
            inProgress.add(httpClient.sendAsync(request(ownerToken, body), HttpResponse.BodyHandlers.ofByteArray()));
        }
        assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();

        HttpResponse<byte[]> rejected = download(ownerToken, body);
        int activeConnections = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
        resume.countDown();

        assertThat(rejected.statusCode()).isEqualTo(503);
        assertThat(text(rejected)).contains("\"success\":false").contains("JOB_SUBMISSION_503_DOWNLOAD");
        assertThat(activeConnections).isZero();
        for (CompletableFuture<HttpResponse<byte[]>> response : inProgress) {
            assertThat(response.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(unzip(response.get().body())).hasSize(1);
        }
        assertThat(download(ownerToken, body).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("전송 중에 저장소 읽기가 실패하면 JSON 오류를 덧붙이지 않고 응답을 끝맺지 않은 채 연결을 끊으며 슬롯과 스트림을 돌려준다")
    void abortsConnectionWhenStorageFailsMidStream() throws Exception {
        objects.put(key(menuUrl), () -> new FailingStream(LARGE_CONTENT));
        String body = body(posterJobId, List.of(posterRevisionUrl), menuJobId, List.of(menuUrl));

        for (int attempt = 0; attempt <= MAX_CONCURRENCY; attempt++) {
            RawResponse response = rawDownload(ownerToken, body, Integer.MAX_VALUE);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.terminated()).as("끝 청크 없이 연결이 끊겨야 한다").isFalse();
            assertThat(response.body().length).isGreaterThan(LARGE_CONTENT.length);
            assertThat(new String(response.body(), StandardCharsets.ISO_8859_1))
                    .doesNotContain("\"success\"").doesNotContain("\"timestamp\"").doesNotContain("\"error\"");
            assertThat(isCompleteZip(response.body())).isFalse();
        }

        objects.put(key(menuUrl), () -> new ByteArrayInputStream(new byte[] { 1 }));
        assertThat(download(ownerToken, body).statusCode()).isEqualTo(200);
        assertStreamsReleased();
    }

    @Test
    @DisplayName("전송을 시작하기 전에 첫 파일을 열지 못하면 ZIP 헤더를 지우고 JOB_SUBMISSION_502를 ApiResponse 형식으로 응답한다")
    void respondsWithErrorWhenFirstFileCannotBeOpened() throws Exception {
        objects.put(key(menuUrl), () -> {
            throw software.amazon.awssdk.core.exception.SdkClientException.create("connection reset");
        });

        HttpResponse<byte[]> response = download(ownerToken, body(menuJobId, List.of(menuUrl)));

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                contentType -> assertThat(contentType).startsWith("application/json"));
        assertThat(response.headers().firstValue("Content-Disposition")).isEmpty();
        assertThat(text(response)).contains("\"success\":false").contains("JOB_SUBMISSION_502");
    }

    @Test
    @DisplayName("받는 쪽이 중간에 연결을 끊으면 전송을 멈추고 슬롯과 저장소 스트림을 돌려준다")
    void releasesResourcesWhenClientCancels() throws Exception {
        objects.put(key(menuUrl), () -> new EndlessStream());
        String body = body(menuJobId, List.of(menuUrl));

        for (int attempt = 0; attempt <= MAX_CONCURRENCY; attempt++) {
            RawResponse response = rawDownload(ownerToken, body, 64 * 1024);

            assertThat(response.status()).isEqualTo(200);
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(this::assertStreamsReleased);
        objects.put(key(menuUrl), () -> new ByteArrayInputStream(new byte[] { 1 }));
        assertThat(download(ownerToken, body).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("저장소 읽기가 인터럽트로 끝나지 않아도 서버 타임아웃을 넘기면 읽기를 끊고 연결을 끊으며, 작업이 끝난 뒤 슬롯을 돌려준다")
    void abortsConnectionOnServerTimeout() throws Exception {
        // 취소가 읽기를 끊지 못하는 경우에도 테스트가 끝나면 작업을 풀어 준다
        CountDownLatch resume = new CountDownLatch(1);
        objects.put(key(menuUrl), () -> new UninterruptibleStream(resume));
        String body = body(posterJobId, List.of(posterRevisionUrl), menuJobId, List.of(menuUrl));

        try {
            for (int attempt = 0; attempt < MAX_CONCURRENCY; attempt++) {
                RawResponse response = rawDownload(ownerToken, body, Integer.MAX_VALUE);

                assertThat(response.status()).isEqualTo(200);
                assertThat(response.terminated()).as("끝 청크 없이 연결이 끊겨야 한다").isFalse();
                assertThat(new String(response.body(), StandardCharsets.ISO_8859_1))
                        .doesNotContain("\"success\"").doesNotContain("\"timestamp\"").doesNotContain("\"error\"");
            }

            // 테스트가 풀어 주기 전에, 타임아웃 취소만으로 읽기가 끊기고 슬롯이 돌아와야 한다
            await().atMost(Duration.ofSeconds(10)).untilAsserted(this::assertStreamsReleased);
            objects.put(key(menuUrl), () -> new ByteArrayInputStream(new byte[] { 1 }));
            assertThat(download(ownerToken, body).statusCode()).isEqualTo(200);
        } finally {
            resume.countDown();
        }
    }

    private void assertStreamsReleased() {
        assertThat(releasedStreams.get()).isEqualTo(openedStreams.get());
    }

    private HttpResponse<byte[]> download(String token, String body) throws IOException, InterruptedException {
        return httpClient.send(request(token, body), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest request(String token, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/jobs/submissions/download"))
                .header("Content-Type", "application/json")
                .header("Origin", ORIGIN)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request.build();
    }

    /** 응답이 끝 청크로 끝맺었는지 보려고 소켓으로 직접 받는다. maxBodyBytes만큼 받으면 받는 쪽에서 연결을 끊는다. */
    private RawResponse rawDownload(String token, String body, int maxBodyBytes) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(15_000);
            OutputStream output = socket.getOutputStream();
            output.write(("POST /jobs/submissions/download HTTP/1.1\r\n"
                    + "Host: localhost:" + port + "\r\n"
                    + "Authorization: Bearer " + token + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + payload.length + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(payload);
            output.flush();

            ByteArrayOutputStream received = new ByteArrayOutputStream();
            InputStream input = socket.getInputStream();
            byte[] buffer = new byte[8192];
            int read;
            while (received.size() < maxBodyBytes && (read = input.read(buffer)) >= 0) {
                received.write(buffer, 0, read);
            }
            return RawResponse.parse(received.toByteArray());
        }
    }

    private static String body(Long jobId, List<String> fileUrls) {
        return "{\"jobs\":[" + job(jobId, fileUrls) + "]}";
    }

    private static String body(Long firstJobId, List<String> firstFileUrls, Long secondJobId,
            List<String> secondFileUrls) {
        return "{\"jobs\":[" + job(firstJobId, firstFileUrls) + "," + job(secondJobId, secondFileUrls) + "]}";
    }

    private static String job(Long jobId, List<String> fileUrls) {
        return "{\"jobId\":" + jobId + ",\"fileUrls\":[\"" + String.join("\",\"", fileUrls) + "\"]}";
    }

    private static String text(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                entries.put(entry.getName(), input.readAllBytes());
            }
        }
        return entries;
    }

    /** ZIP은 끝 레코드(End of central directory)가 있어야 압축 프로그램이 정상 파일로 연다. */
    private static boolean isCompleteZip(byte[] bytes) {
        byte[] endOfCentralDirectory = { 'P', 'K', 5, 6 };
        for (int index = bytes.length - endOfCentralDirectory.length; index >= 0; index--) {
            if (Arrays.equals(bytes, index, index + endOfCentralDirectory.length, endOfCentralDirectory, 0,
                    endOfCentralDirectory.length)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(7).nextBytes(bytes);
        return bytes;
    }

    private Long saveOwner(String userId, String username, String businessNumber) {
        userIds.add(userId);
        userRepository.saveAndFlush(User.builder()
                .id(userId).username(username).isLock(false).role(UserRole.OWNER).build());
        Long ownerProfileId = ownerRepository.saveAndFlush(Owner.builder()
                .userId(userId).businessNumber(businessNumber).storeName("ZIP 테스트 매장").categoryId(1L)
                .build()).getId();
        ownerProfileIds.add(ownerProfileId);
        return ownerProfileId;
    }

    private Long saveMatchedJob(Long ownerProfileId, String title) {
        Job job = Job.create(ownerProfileId, title, "설명", 100_000L,
                LocalDate.now(), LocalDate.now().plusDays(3), 2, null);
        job.match(studentProfileId);
        Long jobId = jobRepository.saveAndFlush(job).getId();
        jobIds.add(jobId);
        return jobId;
    }

    private void saveSubmission(JobSubmission submission) {
        submissionIds.add(jobSubmissionRepository.saveAndFlush(submission).getId());
    }

    /** 가짜 저장소에 객체를 두고, 학생이 업로드 준비로 받았을 공개 URL을 돌려준다. */
    private String putObject(Long jobId, String fileName, byte[] content) {
        String key = "job-submissions/" + jobId + "/" + studentProfileId + "/" + UUID.randomUUID() + "/" + fileName;
        objects.put(key, () -> new ByteArrayInputStream(content));
        return s3Utilities.getUrl(url -> url.bucket(bucket).key(key)).toString();
    }

    private String key(String fileUrl) {
        return objects.keySet().stream()
                .filter(key -> s3Utilities.getUrl(url -> url.bucket(bucket).key(key)).toString().equals(fileUrl))
                .findFirst().orElseThrow();
    }

    private record RawResponse(int status, byte[] body, boolean terminated) {

        /** 청크 전송을 풀어 본문을 모으고, 크기 0인 끝 청크까지 받았는지 기록한다. */
        static RawResponse parse(byte[] received) {
            String raw = new String(received, StandardCharsets.ISO_8859_1);
            int headerEnd = raw.indexOf("\r\n\r\n");
            int status = Integer.parseInt(raw.substring(9, 12));
            assertThat(raw.substring(0, headerEnd).toLowerCase()).contains("transfer-encoding: chunked");

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            int position = headerEnd + 4;
            while (true) {
                int lineEnd = raw.indexOf("\r\n", position);
                if (lineEnd < 0) {
                    return new RawResponse(status, body.toByteArray(), false);
                }
                int size = Integer.parseInt(raw.substring(position, lineEnd).trim(), 16);
                if (size == 0) {
                    return new RawResponse(status, body.toByteArray(), true);
                }
                int chunkStart = lineEnd + 2;
                int available = Math.min(size, received.length - chunkStart);
                body.write(received, chunkStart, Math.max(available, 0));
                if (available < size) {
                    return new RawResponse(status, body.toByteArray(), false);
                }
                position = chunkStart + size + 2;
            }
        }
    }

    /** 읽기를 끝냈거나(close) 버렸을 때(abort 포함) 한 번만 센다. */
    private final class ReleaseCountingStream extends InputStream implements Abortable {

        private final InputStream delegate;
        private boolean released;

        private ReleaseCountingStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public void abort() {
            if (delegate instanceof Abortable abortable) {
                abortable.abort();
            }
            release();
        }

        @Override
        public void close() throws IOException {
            release();
            delegate.close();
        }

        private synchronized void release() {
            if (!released) {
                released = true;
                releasedStreams.incrementAndGet();
            }
        }
    }

    /** 블로킹 소켓 읽기처럼 인터럽트로는 깨어나지 않고, 끊어야(abort) 읽기가 끝난다. */
    private static final class UninterruptibleStream extends InputStream implements Abortable {

        private final CountDownLatch aborted = new CountDownLatch(1);
        private final CountDownLatch testFinished;

        private UninterruptibleStream(CountDownLatch testFinished) {
            this.testFinished = testFinished;
        }

        @Override
        public int read() throws IOException {
            while (testFinished.getCount() > 0) {
                try {
                    if (aborted.await(50, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                } catch (InterruptedException ignored) {
                    // 인터럽트로는 끝나지 않는다
                }
            }
            throw new IOException("stream aborted");
        }

        @Override
        public void abort() {
            aborted.countDown();
        }
    }

    /** 읽기 시작을 알리고, 풀어 줄 때까지 내용을 내주지 않는다. */
    private static final class GatedStream extends ByteArrayInputStream {

        private final CountDownLatch reading;
        private final CountDownLatch resume;

        private GatedStream(byte[] content, CountDownLatch reading, CountDownLatch resume) {
            super(content);
            this.reading = reading;
            this.resume = resume;
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length) {
            reading.countDown();
            try {
                resume.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return super.read(buffer, offset, length);
        }
    }

    /** 내용을 다 내준 뒤 연결이 끊긴 것처럼 실패한다. */
    private static final class FailingStream extends ByteArrayInputStream {

        private FailingStream(byte[] content) {
            super(content);
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length) throws UncheckedIOExceptionCarrier {
            int read = super.read(buffer, offset, length);
            if (read < 0) {
                throw new UncheckedIOExceptionCarrier();
            }
            return read;
        }
    }

    /** ByteArrayInputStream.read는 IOException을 선언하지 않아, 저장소 SDK처럼 런타임 예외로 실패를 알린다. */
    private static final class UncheckedIOExceptionCarrier extends RuntimeException {

        private UncheckedIOExceptionCarrier() {
            super("connection reset");
        }
    }

    /** 끝나지 않는 내용. 받는 쪽이 끊지 않으면 전송이 멈추지 않는다. */
    private static final class EndlessStream extends InputStream {

        private final Random random = new Random(11);

        @Override
        public int read() {
            return random.nextInt(256);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            for (int index = offset; index < offset + length; index++) {
                buffer[index] = (byte) random.nextInt(256);
            }
            return length;
        }
    }
}
