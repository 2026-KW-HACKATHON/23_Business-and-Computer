package com.gakkum.backend.application.media.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import com.gakkum.backend.domain.media.dto.MediaQueryDto.PrepareImageUploadResult;
import com.gakkum.backend.global.response.KoreaTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PrepareImageUploadResponse {

    private final String uploadUrl;
    private final Map<String, String> uploadHeaders;
    private final OffsetDateTime uploadUrlExpiresAt;
    private final String imageUrl;

    public static PrepareImageUploadResponse from(PrepareImageUploadResult result) {
        return PrepareImageUploadResponse.builder()
                .uploadUrl(result.getUploadUrl())
                .uploadHeaders(result.getUploadHeaders())
                .uploadUrlExpiresAt(KoreaTime.from(result.getUploadUrlExpiresAt()))
                .imageUrl(result.getImageUrl())
                .build();
    }
}
