package com.gakkum.backend.application.job.dto;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CancelJobCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 의뢰 취소 요청. 취소 이유와 학생에게 남길 말은 모두 필수 자유 입력이다. */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobCancelRequest {

    @NotBlank
    @Size(max = 5000)
    private String cancelReason;

    @NotBlank
    @Size(max = 5000)
    private String messageToStudent;

    public static JobCancelRequest of(String cancelReason, String messageToStudent) {
        return JobCancelRequest.builder()
                .cancelReason(cancelReason)
                .messageToStudent(messageToStudent)
                .build();
    }

    public CancelJobCommand toCommand(String username, Long jobId) {
        return CancelJobCommand.of(username, jobId, cancelReason.trim(), messageToStudent.trim());
    }
}
