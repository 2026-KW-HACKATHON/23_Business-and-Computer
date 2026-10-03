package com.gakkum.backend.domain.payment.dto;

import java.time.Instant;
import java.time.LocalDate;
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

        private final PaymentHistorySummaryResult summary;
        private final List<PaymentHistoryMonthResult> months;

        public static PaymentHistoryResult of(
                PaymentHistorySummaryResult summary, List<PaymentHistoryMonthResult> months) {
            return new PaymentHistoryResult(summary, months);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PaymentHistorySummaryResult {

        private final Long thisMonthPaymentAmount;
        private final Long heldAmount;
        private final Long totalSettledAmount;

        public static PaymentHistorySummaryResult of(
                Long thisMonthPaymentAmount, Long heldAmount, Long totalSettledAmount) {
            return new PaymentHistorySummaryResult(thisMonthPaymentAmount, heldAmount, totalSettledAmount);
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

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SettlementHistoryData {

        private final Long jobId;
        private final Long jobApplicationId;
        private final Long amount;
        private final Long studentCompensationAmount;
        private final PaymentStatus status;
        private final Instant approvedAt;
        private final Instant refundedAt;

        public static SettlementHistoryData from(Payment payment) {
            return new SettlementHistoryData(payment.getJobId(), payment.getJobApplicationId(), payment.getAmount(),
                    payment.getStudentCompensationAmount(), payment.getStatus(), payment.getApprovedAt(),
                    payment.getRefundedAt());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SettlementHistoryResult {

        private final SettlementHistorySummaryResult summary;
        private final List<SettlementHistoryMonthResult> months;

        public static SettlementHistoryResult of(
                SettlementHistorySummaryResult summary, List<SettlementHistoryMonthResult> months) {
            return new SettlementHistoryResult(summary, months);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SettlementHistorySummaryResult {

        private final Long thisMonthWorkAmount;
        private final Long scheduledAmount;
        private final Long totalSettledAmount;

        public static SettlementHistorySummaryResult of(
                Long thisMonthWorkAmount, Long scheduledAmount, Long totalSettledAmount) {
            return new SettlementHistorySummaryResult(thisMonthWorkAmount, scheduledAmount, totalSettledAmount);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SettlementHistoryMonthResult {

        private final String yearMonth;
        private final List<SettlementHistoryItemResult> settlements;

        public static SettlementHistoryMonthResult of(String yearMonth, List<SettlementHistoryItemResult> settlements) {
            return new SettlementHistoryMonthResult(yearMonth, settlements);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SettlementHistoryItemResult {

        private final Long jobId;
        private final String title;
        private final Long amount;
        private final LocalDate settledDate;
        private final String storeName;
        private final SettlementHistoryStatus status;

        public static SettlementHistoryItemResult of(Long jobId, String title, Long amount, LocalDate settledDate,
                String storeName, SettlementHistoryStatus status) {
            return new SettlementHistoryItemResult(jobId, title, amount, settledDate, storeName, status);
        }
    }
}
