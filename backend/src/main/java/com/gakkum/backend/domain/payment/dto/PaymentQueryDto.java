package com.gakkum.backend.domain.payment.dto;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class PaymentQueryDto {

    private PaymentQueryDto() {
    }

    public record PendingPaymentData(String orderId, Long amount, String orderName, String ownerUserId) {
    }

    public record ApprovedPaymentData(String orderId, Long amount, Instant approvedAt) {
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PreparePaymentResult {

        private final String orderId;
        private final Long amount;
        private final String orderName;
        private final String nextRedirectPcUrl;
        private final String nextRedirectMobileUrl;

        public static PreparePaymentResult of(
                String orderId, Long amount, String orderName, String nextRedirectPcUrl, String nextRedirectMobileUrl) {
            return new PreparePaymentResult(orderId, amount, orderName, nextRedirectPcUrl, nextRedirectMobileUrl);
        }
    }
}
