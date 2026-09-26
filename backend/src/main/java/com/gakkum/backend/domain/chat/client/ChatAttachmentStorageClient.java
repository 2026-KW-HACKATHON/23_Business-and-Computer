package com.gakkum.backend.domain.chat.client;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;

import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * 채팅 첨부 파일의 S3 업로드·열람 URL 발급과 객체 확인을 담당한다.
 * 메시지에 쓰이지 않은 업로드는 대기 태그 기준 버킷 수명 주기 규칙으로 삭제된다.
 */
@Slf4j
@Component
public class ChatAttachmentStorageClient {

    private static final String UPLOAD_TAG_KEY = "chat-upload";
    private static final String PENDING_TAG_VALUE = "pending";
    private static final String ATTACHED_TAG_VALUE = "attached";
    // 브라우저가 직접 채우거나 설정할 수 없는 헤더는 프론트에 전달하지 않는다
    private static final Set<String> BROWSER_MANAGED_HEADERS = Set.of("host", "content-length");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration uploadUrlTtl;
    private final Duration viewUrlTtl;

    public ChatAttachmentStorageClient(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${s3.bucket}") String bucket,
            @Value("${chat-attachment.upload-url-ttl}") Duration uploadUrlTtl,
            @Value("${chat-attachment.view-url-ttl}") Duration viewUrlTtl) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.uploadUrlTtl = uploadUrlTtl;
        this.viewUrlTtl = viewUrlTtl;
    }

    /** Content-Type, Content-Length, 대기 태그를 서명에 포함한 업로드 URL을 발급한다. */
    public PresignedUpload presignUpload(String key, String contentType, long size) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .tagging(UPLOAD_TAG_KEY + "=" + PENDING_TAG_VALUE)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presign -> presign
                .signatureDuration(uploadUrlTtl)
                .putObjectRequest(request));
        return new PresignedUpload(presigned.url().toString(), browserHeaders(presigned.signedHeaders()),
                presigned.expiration());
    }

    /** IMAGE는 브라우저 기본 표시, FILE은 원래 파일명으로 내려받는 열람 URL을 발급한다. */
    public PresignedView presignView(String key, ChatMessageType type, String fileName) {
        GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(bucket).key(key);
        if (type == ChatMessageType.FILE) {
            request.responseContentDisposition(ContentDisposition.attachment()
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build()
                    .toString());
        }
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presign -> presign
                .signatureDuration(viewUrlTtl)
                .getObjectRequest(request.build()));
        return new PresignedView(presigned.url().toString(), presigned.expiration());
    }

    /** 업로드된 객체의 크기와 형식을 조회한다. 객체가 없으면 빈 값을 반환한다. */
    public Optional<StoredObject> findObject(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return Optional.of(new StoredObject(response.contentLength(), response.contentType()));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw unavailable("head", key, exception);
        } catch (SdkException exception) {
            throw unavailable("head", key, exception);
        }
    }

    /** 메시지에 사용된 객체를 수명 주기 삭제 대상에서 제외한다. */
    public void markAttached(String key) {
        try {
            s3Client.putObjectTagging(PutObjectTaggingRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .tagging(Tagging.builder()
                            .tagSet(Tag.builder().key(UPLOAD_TAG_KEY).value(ATTACHED_TAG_VALUE).build())
                            .build())
                    .build());
        } catch (SdkException exception) {
            throw unavailable("tagging", key, exception);
        }
    }

    private Map<String, String> browserHeaders(Map<String, List<String>> signedHeaders) {
        Map<String, String> headers = new TreeMap<>();
        signedHeaders.forEach((name, values) -> {
            if (!BROWSER_MANAGED_HEADERS.contains(name.toLowerCase())) {
                headers.put(name, String.join(",", values));
            }
        });
        return headers;
    }

    private BusinessException unavailable(String operation, String key, SdkException exception) {
        log.warn("S3 {} failed: key={}, cause={}", operation, key, exception.toString());
        return new BusinessException(ErrorCode.CHAT_UPLOAD_UNAVAILABLE);
    }

    public record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {
    }

    public record PresignedView(String url, Instant expiresAt) {
    }

    public record StoredObject(long size, String contentType) {
    }
}
