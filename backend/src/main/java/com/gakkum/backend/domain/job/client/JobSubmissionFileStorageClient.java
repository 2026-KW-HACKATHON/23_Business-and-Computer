package com.gakkum.backend.domain.job.client;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

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
 * 의뢰 작업물(초안·수정안) 파일을 공개 이미지 버킷의 job-submissions/ 경로에 올릴 URL을 발급하고,
 * 제출된 공개 URL이 해당 의뢰·학생용으로 발급한 경로인지와 업로드 여부를 확인한다.
 * 공개 읽기는 버킷 정책이 담당하므로 URL을 아는 누구나 파일을 열람할 수 있다.
 */
@Slf4j
@Component
public class JobSubmissionFileStorageClient {

    private static final String KEY_ROOT = "job-submissions";
    // 경로 접두사에 해당하는 공개 URL을 얻기 위해 임시로 붙였다 떼는 키 조각
    private static final String PROBE = "probe";
    // 브라우저가 직접 채우거나 설정할 수 없는 헤더는 프론트에 전달하지 않는다
    private static final Set<String> BROWSER_MANAGED_HEADERS = Set.of("host", "content-length");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration uploadUrlTtl;

    public JobSubmissionFileStorageClient(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${s3.image-bucket}") String bucket,
            @Value("${job-submission-file.upload-url-ttl}") Duration uploadUrlTtl) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.uploadUrlTtl = uploadUrlTtl;
    }

    /** 원래 파일명이 공개 URL과 내려받는 파일명에 남도록 UUID 폴더 아래에 그대로 둔다. */
    public String newKey(Long jobId, Long studentProfileId, String fileName) {
        return prefix(jobId, studentProfileId) + UUID.randomUUID() + "/" + fileName;
    }

    /** Content-Type, Content-Length를 서명에 포함한 업로드 URL과 만료되지 않는 공개 URL을 발급한다. */
    public PresignedFileUpload presignUpload(String key, String contentType, long size) {
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
            return new PresignedFileUpload(presigned.url().toString(), browserHeaders(presigned.signedHeaders()),
                    presigned.expiration(), publicUrl(key));
        } catch (SdkException exception) {
            throw unavailable("presign", key, exception);
        }
    }

    /**
     * 이 의뢰·학생용으로 발급한 형태의 공개 URL이면 저장소 키를 반환한다.
     * 키로 공개 URL을 다시 만들어 원문과 정확히 같을 때만 인정하므로 외부 URL이나 변형된 URL은 거부된다.
     */
    public Optional<String> findKey(String fileUrl, Long jobId, Long studentProfileId) {
        String prefix = prefix(jobId, studentProfileId);
        String probeUrl = publicUrl(prefix + PROBE);
        String prefixUrl = probeUrl.substring(0, probeUrl.length() - PROBE.length());
        if (!fileUrl.startsWith(prefixUrl)) {
            return Optional.empty();
        }

        String rest = fileUrl.substring(prefixUrl.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        try {
            UUID folder = UUID.fromString(rest.substring(0, slash));
            String fileName = UriUtils.decode(rest.substring(slash + 1), StandardCharsets.UTF_8);
            if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
                return Optional.empty();
            }
            String key = prefix + folder + "/" + fileName;
            return publicUrl(key).equals(fileUrl) ? Optional.of(key) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** 업로드된 객체의 바이트 크기를 반환한다. 객체가 없으면 빈 값이다. */
    public Optional<Long> findSize(String key) {
        try {
            return Optional.of(s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
                    .contentLength());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw unavailable("head", key, exception);
        } catch (SdkException exception) {
            throw unavailable("head", key, exception);
        }
    }

    private String prefix(Long jobId, Long studentProfileId) {
        return KEY_ROOT + "/" + jobId + "/" + studentProfileId + "/";
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
        log.warn("S3 job submission file {} failed: key={}, cause={}", operation, key, exception.toString());
        return new BusinessException(ErrorCode.JOB_SUBMISSION_FILE_UNAVAILABLE);
    }

    public record PresignedFileUpload(String url, Map<String, String> headers, Instant expiresAt,
            String fileUrl) {
    }
}
