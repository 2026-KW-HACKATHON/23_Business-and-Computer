package com.gakkum.backend.domain.media.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import com.gakkum.backend.domain.media.client.MediaImageStorageClient;
import com.gakkum.backend.domain.media.client.MediaImageStorageClient.PresignedImageUpload;
import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.media.dto.MediaQueryDto.PrepareImageUploadResult;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

/**
 * 프로필·매장·제안 사진의 허용 형식과 크기를 검증하고 업로드 URL을 발급한다.
 * 확장자와 MIME 짝은 설정 실수로 검증이 풀리지 않도록 코드에서 관리한다.
 */
@Service
public class MediaService {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private final MediaImageStorageClient mediaImageStorageClient;
    private final DataSize maxSize;

    public MediaService(
            MediaImageStorageClient mediaImageStorageClient,
            @Value("${media-image.max-size}") DataSize maxSize) {
        this.mediaImageStorageClient = mediaImageStorageClient;
        this.maxSize = maxSize;
    }

    /** 원래 파일명은 저장소 키에 넣지 않고 확장자만 사용한다. */
    public PrepareImageUploadResult prepareImageUpload(String userId, ImagePurpose purpose, String fileName,
            String contentType, long size) {
        String extension = extensionOf(fileName);
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(CONTENT_TYPES.get(extension))) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_TYPE_NOT_ALLOWED);
        }
        if (size > maxSize.toBytes()) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_TOO_LARGE);
        }

        String key = keyPrefix(userId, purpose) + UUID.randomUUID() + "." + extension;
        PresignedImageUpload presigned = mediaImageStorageClient.presignUpload(key, normalized, size);
        return PrepareImageUploadResult.of(presigned.url(), presigned.headers(),
                toLocalDateTime(presigned.expiresAt()), presigned.imageUrl());
    }

    /**
     * 이 사용자·용도로 발급한 형태의 공개 이미지 URL이면 저장소 키를 반환한다.
     * 발급 시와 같이 UUID 파일명과 허용 확장자로 이루어진 키만 인정한다.
     */
    public Optional<String> findImageKey(String userId, ImagePurpose purpose, String imageUrl) {
        String prefix = keyPrefix(userId, purpose);
        return mediaImageStorageClient.findKey(imageUrl, prefix)
                .filter(key -> isIssuedFileName(key.substring(prefix.length())));
    }

    /** 공개 이미지가 실제로 업로드되어 있는지 확인한다. */
    public boolean isImageUploaded(String key) {
        return mediaImageStorageClient.exists(key);
    }

    /**
     * 프로필·매장 사진 URL이 모두 이 사용자가 이 용도로 올린 사진인지 확인한다. 의뢰·제안 사진과 같은 규칙으로,
     * 모든 URL이 발급한 형태인지 먼저 확인한 뒤(아니면 400) 실제 업로드 여부를 확인한다(아니면 409).
     */
    public void validateUploadedImages(String userId, ImagePurpose purpose, Collection<String> imageUrls) {
        List<String> keys = imageUrls.stream()
                .map(imageUrl -> findImageKey(userId, purpose, imageUrl)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_IMAGE_URL_INVALID)))
                .toList();
        for (String key : keys) {
            if (!isImageUploaded(key)) {
                throw new BusinessException(ErrorCode.MEDIA_IMAGE_NOT_UPLOADED);
            }
        }
    }

    private static String keyPrefix(String userId, ImagePurpose purpose) {
        return "images/" + purpose.getKeyPrefix() + "/" + userId + "/";
    }

    private static boolean isIssuedFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || !CONTENT_TYPES.containsKey(fileName.substring(dot + 1))) {
            return false;
        }
        try {
            String uuid = fileName.substring(0, dot);
            return UUID.fromString(uuid).toString().equals(uuid);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // 응답 DTO가 UTC로 해석해 한국 시각으로 바꾸므로 JVM 기본 시간대와 무관하게 UTC로 내린다
    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
