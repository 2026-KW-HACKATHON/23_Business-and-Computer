package com.gakkum.backend.domain.payment.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class PaymentQueryDto {

    private PaymentQueryDto() {
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PreparePaymentResult {

        private final String orderId;
        private final Long amount;
        private final String orderName;

        public static PreparePaymentResult of(String orderId, Long amount, String orderName) {
            return new PreparePaymentResult(orderId, amount, orderName);
        }
    }
}
