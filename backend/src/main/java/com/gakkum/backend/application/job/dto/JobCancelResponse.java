package com.gakkum.backend.application.job.dto;

import java.time.OffsetDateTime;

import com.gakkum.backend.domain.job.dto.JobQueryDto.JobCancelResult;
import com.gakkum.backend.global.response.KoreaTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobCancelResponse {

    private final Long jobId;
    private final String status;
    private final Long paidAmount;
    private final Long studentCompensationAmount;
    private final Long refundAmount;
    private final OffsetDateTime cancelledAt;
    private final String cancelReason;
    private final String messageToStudent;

    public static JobCancelResponse from(JobCancelResult result) {
        return JobCancelResponse.builder()
                .jobId(result.getJobId())
                .status(result.getStatus())
                .paidAmount(result.getPaidAmount())
                .studentCompensationAmount(result.getStudentCompensationAmount())
                .refundAmount(result.getRefundAmount())
                .cancelledAt(KoreaTime.from(result.getCancelledAt()))
                .cancelReason(result.getCancelReason())
                .messageToStudent(result.getMessageToStudent())
                .build();
    }
}
