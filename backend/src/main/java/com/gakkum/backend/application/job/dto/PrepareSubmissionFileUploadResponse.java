package com.gakkum.backend.application.job.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.gakkum.backend.domain.job.dto.JobQueryDto.PrepareSubmissionFileUploadResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PrepareSubmissionFileUploadResponse {

    private final String uploadUrl;
    private final Map<String, String> uploadHeaders;
    private final LocalDateTime uploadUrlExpiresAt;
    private final String fileUrl;

    public static PrepareSubmissionFileUploadResponse from(PrepareSubmissionFileUploadResult result) {
        return PrepareSubmissionFileUploadResponse.builder()
                .uploadUrl(result.getUploadUrl())
                .uploadHeaders(result.getUploadHeaders())
                .uploadUrlExpiresAt(result.getUploadUrlExpiresAt())
                .fileUrl(result.getFileUrl())
                .build();
    }
}
