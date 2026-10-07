package com.gakkum.backend.domain.job.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient.PresignedFileUpload;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class JobSubmissionFileStorageClientTest {

    private static final String BUCKET = "gakkum-images-test";
    private static final String BASE_URL = "https://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/";
    private static final String FOLDER = "0b4f2a3e-6a8c-4a39-9f55-8f1d8f0b2c11";

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private JobSubmissionFileStorageClient client;

    @BeforeEach
    void setUp() {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("AKIAEXAMPLE", "secret"));
        s3Client = S3Client.builder().region(Region.AP_NORTHEAST_2).credentialsProvider(credentials).build();
        s3Presigner = S3Presigner.builder().region(Region.AP_NORTHEAST_2).credentialsProvider(credentials).build();
        client = new JobSubmissionFileStorageClient(s3Client, s3Presigner, BUCKET, Duration.ofMinutes(10));
    }

    @AfterEach
    void tearDown() {
        s3Presigner.close();
        s3Client.close();
    }

    @Test
    @DisplayName("저장소 키는 의뢰·학생 경로 아래 UUID 폴더에 원래 파일명을 둔다")
    void createsKeyUnderJobAndStudentPath() {
        String key = client.newKey(42L, 7L, "draft.pdf");

        assertThat(key).matches("job-submissions/42/7/[0-9a-f-]{36}/draft\\.pdf");
    }

    @Test
    @DisplayName("업로드 URL은 형식과 크기를 서명하고 공개 파일 URL을 함께 반환한다")
    void presignsUploadWithPublicFileUrl() {
        String key = "job-submissions/42/7/" + FOLDER + "/draft.pdf";

        PresignedFileUpload upload = client.presignUpload(key, "application/pdf", 1048576);

        String url = URLDecoder.decode(upload.url(), StandardCharsets.UTF_8);
        assertThat(url).startsWith(BASE_URL + key + "?");
        assertThat(url).contains("X-Amz-SignedHeaders=content-length;content-type;host");
        assertThat(url).contains("X-Amz-Expires=600");
        assertThat(upload.headers()).isEqualTo(Map.of("content-type", "application/pdf"));
        assertThat(upload.fileUrl()).isEqualTo(BASE_URL + key);
    }

    @Test
    @DisplayName("발급한 공개 URL은 한글·공백 파일명이어도 원래 저장소 키로 되돌린다")
    void findsKeyFromIssuedUrl() {
        String key = client.newKey(42L, 7L, "가게 포스터 초안.pdf");
        String fileUrl = client.presignUpload(key, "application/pdf", 10).fileUrl();

        assertThat(client.findKey(fileUrl, 42L, 7L)).contains(key);
    }

    @Test
    @DisplayName("다른 의뢰·학생 경로, 외부 URL, 변형된 URL은 거부한다")
    void rejectsForeignOrAlteredUrls() {
        String own = BASE_URL + "job-submissions/42/7/" + FOLDER + "/draft.pdf";

        assertThat(client.findKey(own, 42L, 7L)).isPresent();
        assertThat(client.findKey(own, 43L, 7L)).isEmpty();
        assertThat(client.findKey(own, 42L, 8L)).isEmpty();
        assertThat(client.findKey("https://evil.example.com/job-submissions/42/7/" + FOLDER + "/draft.pdf",
                42L, 7L)).isEmpty();
        assertThat(client.findKey("http://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/job-submissions/42/7/"
                + FOLDER + "/draft.pdf", 42L, 7L)).isEmpty();
        assertThat(client.findKey(own + "?versionId=1", 42L, 7L)).isEmpty();
        assertThat(client.findKey(BASE_URL + "job-submissions/42/7/not-a-uuid/draft.pdf", 42L, 7L)).isEmpty();
        assertThat(client.findKey(BASE_URL + "job-submissions/42/7/" + FOLDER + "/a/draft.pdf", 42L, 7L)).isEmpty();
        assertThat(client.findKey(BASE_URL + "job-submissions/42/7/" + FOLDER + "/", 42L, 7L)).isEmpty();
        assertThat(client.findKey(BASE_URL + "job-submissions/42/7/" + FOLDER + "/%ZZ.pdf", 42L, 7L)).isEmpty();
        assertThat(client.findKey(BASE_URL + "images/profile/u/" + FOLDER + ".png", 42L, 7L)).isEmpty();
    }

    @Test
    @DisplayName("객체가 있으면 HEAD 응답의 바이트 크기를, 404면 빈 값을 반환한다")
    void findsObjectSize() {
        S3Client headClient = mockHeadClient();
        JobSubmissionFileStorageClient headChecking =
                new JobSubmissionFileStorageClient(headClient, s3Presigner, BUCKET, Duration.ofMinutes(10));
        when(headClient.headObject(HeadObjectRequest.builder().bucket(BUCKET).key("found").build()))
                .thenReturn(HeadObjectResponse.builder().contentLength(1048576L).build());
        when(headClient.headObject(HeadObjectRequest.builder().bucket(BUCKET).key("missing").build()))
                .thenThrow(NoSuchKeyException.builder().statusCode(404).build());

        assertThat(headChecking.findSize("found")).contains(1048576L);
        assertThat(headChecking.findSize("missing")).isEmpty();
    }

    @Test
    @DisplayName("404가 아닌 S3 오류나 연결 오류는 JOB_SUBMISSION_502로 거부한다")
    void rejectsWhenHeadFails() {
        S3Client headClient = mockHeadClient();
        JobSubmissionFileStorageClient headChecking =
                new JobSubmissionFileStorageClient(headClient, s3Presigner, BUCKET, Duration.ofMinutes(10));
        when(headClient.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build())
                .thenThrow(SdkClientException.create("timeout"));

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> headChecking.findSize("key"))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.JOB_SUBMISSION_FILE_UNAVAILABLE));
        }
    }

    private S3Client mockHeadClient() {
        S3Client headClient = mock(S3Client.class);
        S3Utilities utilities = s3Client.utilities();
        when(headClient.utilities()).thenReturn(utilities);
        return headClient;
    }
}
