package com.gakkum.backend.application.payment.dto;

import java.time.Instant;

import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedPaymentData;

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

    public static PaymentApproveResponse from(ApprovedPaymentData data) {
        return PaymentApproveResponse.builder()
                .orderId(data.orderId())
                .status("PAID")
                .amount(data.amount())
                .approvedAt(data.approvedAt())
                .build();
    }
}
