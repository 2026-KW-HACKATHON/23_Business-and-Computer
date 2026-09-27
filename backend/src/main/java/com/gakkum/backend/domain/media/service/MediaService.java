package com.gakkum.backend.domain.media.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
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
 * 프로필·매장 사진의 허용 형식과 크기를 검증하고 업로드 URL을 발급한다.
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

        String key = "images/" + purpose.getKeyPrefix() + "/" + userId + "/" + UUID.randomUUID() + "." + extension;
        PresignedImageUpload presigned = mediaImageStorageClient.presignUpload(key, normalized, size);
        return PrepareImageUploadResult.of(presigned.url(), presigned.headers(),
                toLocalDateTime(presigned.expiresAt()), presigned.imageUrl());
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // 만료 시각은 다른 API 응답과 같은 JVM 기본 시간대로 내린다
    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
