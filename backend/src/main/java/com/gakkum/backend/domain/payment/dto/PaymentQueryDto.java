package com.gakkum.backend.domain.payment.dto;

import java.time.Instant;
import java.util.List;

import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;

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

    public record RefundedPaymentData(Long amount, Long studentCompensationAmount, Long refundAmount, Instant refundedAt) {
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

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PaymentHistoryData {

        private final Long jobId;
        private final Long jobApplicationId;
        private final Long amount;
        private final Long refundAmount;
        private final PaymentStatus status;
        private final Instant approvedAt;

        public static PaymentHistoryData from(Payment payment) {
            return new PaymentHistoryData(payment.getJobId(), payment.getJobApplicationId(), payment.getAmount(),
                    payment.getRefundAmount(), payment.getStatus(), payment.getApprovedAt());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PaymentHistoryResult {

        private final List<PaymentHistoryMonthResult> months;

        public static PaymentHistoryResult of(List<PaymentHistoryMonthResult> months) {
            return new PaymentHistoryResult(months);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PaymentHistoryMonthResult {

        private final String yearMonth;
        private final List<PaymentHistoryItemResult> payments;

        public static PaymentHistoryMonthResult of(String yearMonth, List<PaymentHistoryItemResult> payments) {
            return new PaymentHistoryMonthResult(yearMonth, payments);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PaymentHistoryItemResult {

        private final Long jobId;
        private final String title;
        private final Long amount;
        private final Long refundAmount;
        private final Instant approvedAt;
        private final String studentName;
        private final PaymentHistoryStatus status;

        public static PaymentHistoryItemResult of(PaymentHistoryData data, String title, Long refundAmount,
                String studentName, PaymentHistoryStatus status) {
            return new PaymentHistoryItemResult(data.getJobId(), title, data.getAmount(), refundAmount,
                    data.getApprovedAt(), studentName, status);
        }
    }
}
