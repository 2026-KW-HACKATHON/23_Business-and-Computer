package com.gakkum.backend.application.job.dto;

import java.util.HashSet;
import java.util.List;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 초안·수정안 제출 요청. 두 제출은 본문 형식이 같다. */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobSubmissionCreateRequest {

    @NotEmpty
    @Size(max = 10)
    private List<@NotBlank @Size(max = 2048) String> fileUrls;

    @NotBlank
    @Size(max = 5000)
    private String message;

    public static JobSubmissionCreateRequest of(List<String> fileUrls, String message) {
        return JobSubmissionCreateRequest.builder()
                .fileUrls(fileUrls)
                .message(message)
                .build();
    }

    @AssertTrue
    private boolean isFileUrlsDistinct() {
        return fileUrls == null || new HashSet<>(fileUrls).size() == fileUrls.size();
    }

    public CreateJobSubmissionCommand toCommand(String username, Long jobId) {
        return CreateJobSubmissionCommand.of(username, jobId, fileUrls, message.trim());
    }
}
