package com.gakkum.backend.domain.payment.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class PaymentQueryDto {

    private PaymentQueryDto() {
    }

    // 전액·부분 환불은 환불 시 저장한 금액으로만 구분한다. 금액이 저장되지 않은 결제는 전액 환불로 보지 않는다
    private static boolean isFullyRefunded(Long amount, Long refundAmount, Long studentCompensationAmount) {
        return refundAmount != null && refundAmount.equals(amount)
                && studentCompensationAmount != null && studentCompensationAmount == 0L;
    }

    public record PendingPaymentData(String orderId, Long amount, String orderName, String ownerUserId) {
    }

    public record ApprovedPaymentData(String orderId, Long amount, Instant approvedAt) {
    }

    /** 결제 승인 결과. 일반 결제의 의뢰는 진행 중(MATCHED), 제안 결제의 의뢰는 수락 대기(AWAITING_START) 이후 상태다. */
    public record ApprovedOrderData(String orderId, Long amount, Instant approvedAt, Long jobId, JobStatus jobStatus) {
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
        // 제안 결제는 null
        private final Long jobApplicationId;
        // 일반 결제는 null
        private final Long proposalId;
        private final Long amount;
        private final Long refundAmount;
        private final Long studentCompensationAmount;
        private final PaymentStatus status;
        private final Instant approvedAt;
        private final Instant refundedAt;

        public static PaymentHistoryData from(Payment payment) {
            return new PaymentHistoryData(payment.getJobId(), payment.getJobApplicationId(), payment.getProposalId(),
                    payment.getAmount(), payment.getRefundAmount(), payment.getStudentCompensationAmount(),
                    payment.getStatus(), payment.getApprovedAt(), payment.getRefundedAt());
        }

        /** 저장된 환불액이 결제 금액 전액이고 학생 보상금이 0원인 환불인지. 환불되지 않은 결제는 false다. */
        public boolean isFullyRefunded() {
            return PaymentQueryDto.isFullyRefunded(amount, refundAmount, studentCompensationAmount);
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
        // 정산 완료만 값이 있다
        private final LocalDate settledDate;
        // 부분·전액 환불만 값이 있다
        private final LocalDate refundedDate;

        public static PaymentHistoryItemResult of(PaymentHistoryData data, String title, Long refundAmount,
                String studentName, PaymentHistoryStatus status, LocalDate settledDate, LocalDate refundedDate) {
            return new PaymentHistoryItemResult(data.getJobId(), title, data.getAmount(), refundAmount,
                    data.getApprovedAt(), studentName, status, settledDate, refundedDate);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SettlementHistoryData {

        private final Long jobId;
        // 제안 결제는 null
        private final Long jobApplicationId;
        // 일반 결제는 null
        private final Long proposalId;
        private final Long amount;
        private final Long refundAmount;
        private final Long studentCompensationAmount;
        private final PaymentStatus status;
        private final Instant approvedAt;
        private final Instant refundedAt;

        public static SettlementHistoryData from(Payment payment) {
            return new SettlementHistoryData(payment.getJobId(), payment.getJobApplicationId(),
                    payment.getProposalId(), payment.getAmount(), payment.getRefundAmount(),
                    payment.getStudentCompensationAmount(), payment.getStatus(), payment.getApprovedAt(),
                    payment.getRefundedAt());
        }

        /** 저장된 환불액이 결제 금액 전액이고 학생 보상금이 0원인 환불인지. 환불되지 않은 결제는 false다. */
        public boolean isFullyRefunded() {
            return PaymentQueryDto.isFullyRefunded(amount, refundAmount, studentCompensationAmount);
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
