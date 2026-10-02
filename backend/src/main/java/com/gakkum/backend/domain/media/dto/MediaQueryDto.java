package com.gakkum.backend.domain.media.dto;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class MediaQueryDto {

    private MediaQueryDto() {
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PrepareImageUploadResult {
        private final String uploadUrl;
        private final Map<String, String> uploadHeaders;
        private final LocalDateTime uploadUrlExpiresAt;
        private final String imageUrl;

        public static PrepareImageUploadResult of(String uploadUrl, Map<String, String> uploadHeaders,
                LocalDateTime uploadUrlExpiresAt, String imageUrl) {
            return new PrepareImageUploadResult(uploadUrl, uploadHeaders, uploadUrlExpiresAt, imageUrl);
        }
    }
}
