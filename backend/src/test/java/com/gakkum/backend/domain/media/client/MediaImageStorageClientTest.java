package com.gakkum.backend.domain.media.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.media.client.MediaImageStorageClient.PresignedImageUpload;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class MediaImageStorageClientTest {

    private static final String BUCKET = "gakkum-images-test";
    private static final String KEY = "images/profile/01K58M6PJV8VAJMXHBHJ2PNB5C/upload-1.png";

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private MediaImageStorageClient client;

    @BeforeEach
    void setUp() {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("AKIAEXAMPLE", "secret"));
        s3Client = S3Client.builder().region(Region.AP_NORTHEAST_2).credentialsProvider(credentials).build();
        s3Presigner = S3Presigner.builder().region(Region.AP_NORTHEAST_2).credentialsProvider(credentials).build();
        client = new MediaImageStorageClient(s3Client, s3Presigner, BUCKET, Duration.ofMinutes(10));
    }

    @AfterEach
    void tearDown() {
        s3Presigner.close();
        s3Client.close();
    }

    @Test
    @DisplayName("업로드 URL은 형식과 크기를 서명하고 브라우저가 보낼 헤더와 공개 이미지 URL을 반환한다")
    void presignsUploadWithSignedHeadersAndPublicUrl() {
        PresignedImageUpload upload = client.presignUpload(KEY, "image/png", 482133);

        String url = URLDecoder.decode(upload.url(), StandardCharsets.UTF_8);
        String objectUrl = "https://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/" + KEY;
        assertThat(url).startsWith(objectUrl + "?");
        assertThat(url).contains("X-Amz-SignedHeaders=content-length;content-type;host");
        assertThat(url).contains("X-Amz-Expires=600");
        assertThat(url).doesNotContain("x-amz-tagging", "x-amz-acl");
        assertThat(upload.headers()).isEqualTo(Map.of("content-type", "image/png"));
        assertThat(upload.expiresAt()).isCloseTo(Instant.now().plus(Duration.ofMinutes(10)),
                within(5, ChronoUnit.SECONDS));
        assertThat(upload.imageUrl()).isEqualTo(objectUrl);
    }

    @Test
    @DisplayName("서명 중 S3 SDK 오류가 나면 MEDIA_UPLOAD_502로 거부한다")
    @SuppressWarnings("unchecked")
    void rejectsWhenPresignFails() {
        S3Presigner failingPresigner = mock(S3Presigner.class);
        when(failingPresigner.presignPutObject(any(Consumer.class)))
                .thenThrow(SdkClientException.create("Unable to load credentials"));
        MediaImageStorageClient failingClient = new MediaImageStorageClient(
                s3Client, failingPresigner, BUCKET, Duration.ofMinutes(10));

        assertThatThrownBy(() -> failingClient.presignUpload(KEY, "image/png", 482133))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA_UPLOAD_UNAVAILABLE));
    }
}
