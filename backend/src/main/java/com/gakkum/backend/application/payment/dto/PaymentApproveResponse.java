package com.gakkum.backend.application.payment.dto;

import java.time.Instant;

import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedOrderData;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentApproveResponse {

    private final String orderId;
    private final String status;
    private final Long amount;
    private final Instant approvedAt;
    private final Long jobId;
    // 일반 결제는 MATCHED, 제안 결제는 학생이 작업을 시작하기 전까지 AWAITING_START
    private final JobStatus jobStatus;

    public static PaymentApproveResponse from(ApprovedOrderData data) {
        return PaymentApproveResponse.builder()
                .orderId(data.orderId())
                .status("PAID")
                .amount(data.amount())
                .approvedAt(data.approvedAt())
                .jobId(data.jobId())
                .jobStatus(data.jobStatus())
                .build();
    }
}
