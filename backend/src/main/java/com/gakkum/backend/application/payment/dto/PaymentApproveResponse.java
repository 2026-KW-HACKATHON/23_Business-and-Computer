package com.gakkum.backend.application.payment.dto;

import java.time.Instant;

import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedPaymentData;

public record PaymentApproveResponse(String orderId, String status, Long amount, Instant approvedAt) {

    public static PaymentApproveResponse from(ApprovedPaymentData data) {
        return new PaymentApproveResponse(data.orderId(), "PAID", data.amount(), data.approvedAt());
    }
}
