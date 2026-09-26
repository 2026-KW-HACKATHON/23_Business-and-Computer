package com.gakkum.backend.application.chat.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.gakkum.backend.domain.chat.dto.ChatQueryDto.PrepareAttachmentUploadResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PrepareAttachmentUploadResponse {

    private final UUID uploadId;
    private final String uploadUrl;
    private final Map<String, String> uploadHeaders;
    private final LocalDateTime uploadUrlExpiresAt;

    public static PrepareAttachmentUploadResponse from(PrepareAttachmentUploadResult result) {
        return PrepareAttachmentUploadResponse.builder()
                .uploadId(result.getUploadId())
                .uploadUrl(result.getUploadUrl())
                .uploadHeaders(result.getUploadHeaders())
                .uploadUrlExpiresAt(result.getUploadUrlExpiresAt())
                .build();
    }
}
