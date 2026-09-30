package com.gakkum.backend.domain.media.client;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * 프로필·매장·제안 사진을 공개 이미지 버킷에 올릴 업로드 URL과 공개 URL을 발급하고,
 * 제출된 공개 URL의 저장소 키와 업로드 여부를 확인한다.
 * 공개 읽기는 버킷 정책이 담당하므로 객체별 ACL은 지정하지 않는다.
 */
@Slf4j
@Component
public class MediaImageStorageClient {

    // 경로 접두사에 해당하는 공개 URL을 얻기 위해 임시로 붙였다 떼는 키 조각
    private static final String PROBE = "probe";
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
            return new PresignedImageUpload(presigned.url().toString(), browserHeaders(presigned.signedHeaders()),
                    presigned.expiration(), publicUrl(key));
        } catch (SdkException exception) {
            throw unavailable("presign", key, exception);
        }
    }

    /**
     * 공개 URL이 주어진 키 접두사 바로 아래 객체를 가리키면 저장소 키를 반환한다.
     * 키로 공개 URL을 다시 만들어 원문과 정확히 같을 때만 인정하므로 외부 URL이나 변형된 URL은 거부된다.
     */
    public Optional<String> findKey(String imageUrl, String keyPrefix) {
        String probeUrl = publicUrl(keyPrefix + PROBE);
        String prefixUrl = probeUrl.substring(0, probeUrl.length() - PROBE.length());
        if (!imageUrl.startsWith(prefixUrl)) {
            return Optional.empty();
        }

        String name = imageUrl.substring(prefixUrl.length());
        if (name.isEmpty() || name.contains("/")) {
            return Optional.empty();
        }
        String key = keyPrefix + name;
        return publicUrl(key).equals(imageUrl) ? Optional.of(key) : Optional.empty();
    }

    /** 객체가 업로드되어 있는지 확인한다. */
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw unavailable("head", key, exception);
        } catch (SdkException exception) {
            throw unavailable("head", key, exception);
        }
    }

    private String publicUrl(String key) {
        return s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucket).key(key).build())
                .toString();
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
        log.warn("S3 image {} failed: key={}, cause={}", operation, key, exception.toString());
        return new BusinessException(ErrorCode.MEDIA_UPLOAD_UNAVAILABLE);
    }

    public record PresignedImageUpload(String url, Map<String, String> headers, Instant expiresAt,
            String imageUrl) {
    }
}
