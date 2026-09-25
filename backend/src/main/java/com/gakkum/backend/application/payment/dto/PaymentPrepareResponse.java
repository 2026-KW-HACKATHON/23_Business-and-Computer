package com.gakkum.backend.application.payment.dto;

import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PreparePaymentResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentPrepareResponse {

    private final String orderId;
    private final Long amount;
    private final String orderName;

    public static PaymentPrepareResponse from(PreparePaymentResult result) {
        return PaymentPrepareResponse.builder()
                .orderId(result.getOrderId())
                .amount(result.getAmount())
                .orderName(result.getOrderName())
                .build();
    }
}
