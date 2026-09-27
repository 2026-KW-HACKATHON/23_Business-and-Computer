package com.gakkum.backend.application.job.dto;

import com.gakkum.backend.domain.job.dto.JobCommandDto.PrepareSubmissionFileUploadCommand;
import com.gakkum.backend.domain.job.dto.JobSubmissionFileType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PrepareSubmissionFileUploadRequest {

    @NotNull
    private JobSubmissionFileType type;

    // 파일명은 저장소 경로에 그대로 쓰이므로 경로 구분자와 제어 문자를 거부한다
    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[^/\\\\\\p{Cntrl}]+")
    private String fileName;

    @NotBlank
    @Size(max = 100)
    private String contentType;

    @NotNull
    @Positive
    private Long size;

    public static PrepareSubmissionFileUploadRequest of(JobSubmissionFileType type, String fileName,
            String contentType, Long size) {
        return PrepareSubmissionFileUploadRequest.builder()
                .type(type)
                .fileName(fileName)
                .contentType(contentType)
                .size(size)
                .build();
    }

    public PrepareSubmissionFileUploadCommand toCommand(String username, Long jobId) {
        return PrepareSubmissionFileUploadCommand.of(username, jobId, type, fileName, contentType, size);
    }
}
