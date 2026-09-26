package com.gakkum.backend.domain.chat.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.domain.chat.client.ChatAttachmentStorageClient.PresignedUpload;
import com.gakkum.backend.domain.chat.client.ChatAttachmentStorageClient.PresignedView;
import com.gakkum.backend.domain.chat.client.ChatAttachmentStorageClient.StoredObject;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class ChatAttachmentStorageClientTest {

    private static final String BUCKET = "gakkum-test";
    private static final String KEY = "chat/room-1/upload-1.png";

    private final S3Client s3Client = mock(S3Client.class);
    private S3Presigner s3Presigner;
    private ChatAttachmentStorageClient client;

    @BeforeEach
    void setUp() {
        s3Presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("AKIAEXAMPLE", "secret")))
                .build();
        client = new ChatAttachmentStorageClient(s3Client, s3Presigner, BUCKET,
                Duration.ofMinutes(10), Duration.ofMinutes(15));
    }

    @AfterEach
    void tearDown() {
        s3Presigner.close();
    }

    @Test
    @DisplayName("업로드 URL은 형식, 크기, 대기 태그를 서명하고 브라우저가 보낼 헤더만 반환한다")
    void presignsUploadWithSignedHeaders() {
        PresignedUpload upload = client.presignUpload(KEY, "image/png", 482133);

        String url = URLDecoder.decode(upload.url(), StandardCharsets.UTF_8);
        assertThat(url).startsWith("https://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/" + KEY);
        assertThat(url).contains("X-Amz-SignedHeaders=content-length;content-type;host;x-amz-tagging");
        assertThat(upload.headers()).isEqualTo(Map.of(
                "content-type", "image/png",
                "x-amz-tagging", "chat-upload=pending"));
        assertThat(upload.expiresAt()).isCloseTo(Instant.now().plus(Duration.ofMinutes(10)),
                within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("FILE 열람 URL은 원래 파일명으로 내려받도록 서명한다")
    void presignsFileViewAsAttachment() {
        PresignedView view = client.presignView("chat/room-1/upload-2.pdf", ChatMessageType.FILE, "견적서 최종.pdf");

        String url = URLDecoder.decode(view.url(), StandardCharsets.UTF_8);
        assertThat(url).contains("response-content-disposition=attachment;");
        assertThat(url).contains("filename*=UTF-8''%EA%B2%AC%EC%A0%81%EC%84%9C%20%EC%B5%9C%EC%A2%85.pdf");
        assertThat(view.expiresAt()).isCloseTo(Instant.now().plus(Duration.ofMinutes(15)),
                within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("IMAGE 열람 URL은 내려받기 지정 없이 서명한다")
    void presignsImageViewInline() {
        PresignedView view = client.presignView(KEY, ChatMessageType.IMAGE, "시안.png");

        assertThat(view.url()).startsWith("https://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/" + KEY);
        assertThat(view.url()).doesNotContain("response-content-disposition");
    }

    @Test
    @DisplayName("업로드된 객체의 크기와 형식을 조회한다")
    void findsStoredObject() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(482133L).contentType("image/png").build());

        assertThat(client.findObject(KEY)).contains(new StoredObject(482133L, "image/png"));
    }

    @Test
    @DisplayName("업로드되지 않은 객체는 빈 값으로 조회한다")
    void returnsEmptyWhenObjectMissing() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        assertThat(client.findObject(KEY)).isEmpty();
    }

    @Test
    @DisplayName("S3 조회와 태그 변경에 실패하면 CHAT_UPLOAD_502로 거부한다")
    void rejectsWhenStorageUnavailable() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("Forbidden").build());
        when(s3Client.putObjectTagging(any(PutObjectTaggingRequest.class)))
                .thenThrow(SdkClientException.create("connection refused"));

        assertThatThrownBy(() -> client.findObject(KEY))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_UPLOAD_UNAVAILABLE));
        assertThatThrownBy(() -> client.markAttached(KEY))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_UPLOAD_UNAVAILABLE));
    }

    @Test
    @DisplayName("메시지에 사용된 객체의 태그를 사용됨으로 바꾼다")
    void marksObjectAttached() {
        client.markAttached(KEY);

        ArgumentCaptor<PutObjectTaggingRequest> captor = ArgumentCaptor.forClass(PutObjectTaggingRequest.class);
        verify(s3Client).putObjectTagging(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(KEY);
        assertThat(captor.getValue().tagging().tagSet())
                .singleElement()
                .satisfies(tag -> {
                    assertThat(tag.key()).isEqualTo("chat-upload");
                    assertThat(tag.value()).isEqualTo("attached");
                });
    }
}
