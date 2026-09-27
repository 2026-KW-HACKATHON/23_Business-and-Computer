package com.gakkum.backend.domain.media.client;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * 프로필·매장 사진을 공개 이미지 버킷에 올릴 업로드 URL과 공개 URL을 발급한다.
 * 공개 읽기는 버킷 정책이 담당하므로 객체별 ACL은 지정하지 않는다.
 */
@Slf4j
@Component
public class MediaImageStorageClient {

    // 브라우저가 직접 채우거나 설정할 수 없는 헤더는 프론트에 전달하지 않는다
    private static final Set<String> BROWSER_MANAGED_HEADERS = Set.of("host", "content-length");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration uploadUrlTtl;

    public MediaImageStorageClient(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${s3.image-bucket}") String bucket,
            @Value("${media-image.upload-url-ttl}") Duration uploadUrlTtl) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.uploadUrlTtl = uploadUrlTtl;
    }

    /** Content-Type, Content-Length를 서명에 포함한 업로드 URL과 만료되지 않는 공개 URL을 발급한다. */
    public PresignedImageUpload presignUpload(String key, String contentType, long size) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(size)
                    .build();
            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presign -> presign
                    .signatureDuration(uploadUrlTtl)
                    .putObjectRequest(request));
            String imageUrl = s3Client.utilities()
                    .getUrl(GetUrlRequest.builder().bucket(bucket).key(key).build())
                    .toString();
            return new PresignedImageUpload(presigned.url().toString(), browserHeaders(presigned.signedHeaders()),
                    presigned.expiration(), imageUrl);
        } catch (SdkException exception) {
            log.warn("S3 image presign failed: key={}, cause={}", key, exception.toString());
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_UNAVAILABLE);
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

    public record PresignedImageUpload(String url, Map<String, String> headers, Instant expiresAt,
            String imageUrl) {
    }
}
