package com.gakkum.backend.application.job.dto;

import java.util.HashSet;
import java.util.List;

import com.gakkum.backend.domain.job.dto.JobCommandDto.RequestJobSubmissionRevisionCommand;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 수정 요청. 요청 내용은 필수이고 참고 사진은 선택이다. */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobSubmissionRevisionRequest {

    @NotBlank
    @Size(max = 500)
    private String message;

    // 선택 입력. 최대 4장
    @Size(max = 4)
    private List<@NotBlank String> referenceImageUrls;

    public static JobSubmissionRevisionRequest of(String message, List<String> referenceImageUrls) {
        return JobSubmissionRevisionRequest.builder()
                .message(message)
                .referenceImageUrls(referenceImageUrls)
                .build();
    }

    @AssertTrue(message = "참고 사진 URL은 중복될 수 없습니다.")
    private boolean isReferenceImageUrlsUnique() {
        return referenceImageUrls == null || new HashSet<>(referenceImageUrls).size() == referenceImageUrls.size();
    }

    public RequestJobSubmissionRevisionCommand toCommand(String username, Long jobId, Long submissionId) {
        return RequestJobSubmissionRevisionCommand.of(
                username,
                jobId,
                submissionId,
                message.trim(),
                referenceImageUrls == null ? List.of() : List.copyOf(referenceImageUrls));
    }
}
