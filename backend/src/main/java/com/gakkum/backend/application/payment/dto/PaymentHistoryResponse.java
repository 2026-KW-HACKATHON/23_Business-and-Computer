package com.gakkum.backend.application.payment.dto;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.domain.payment.dto.PaymentHistoryStatus;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryMonthResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistorySummaryResult;
import com.gakkum.backend.global.response.KoreaTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentHistoryResponse {

    private final Summary summary;
    private final List<Month> months;

    public static PaymentHistoryResponse from(PaymentHistoryResult result) {
        return new PaymentHistoryResponse(
                Summary.from(result.getSummary()), result.getMonths().stream().map(Month::from).toList());
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Summary {

        private final Long thisMonthPaymentAmount;
        private final Long heldAmount;
        private final Long totalSettledAmount;

        public static Summary from(PaymentHistorySummaryResult result) {
            return new Summary(
                    result.getThisMonthPaymentAmount(), result.getHeldAmount(), result.getTotalSettledAmount());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Month {

        private final String yearMonth;
        private final List<PaymentHistory> payments;

        public static Month from(PaymentHistoryMonthResult result) {
            return new Month(result.getYearMonth(), result.getPayments().stream().map(PaymentHistory::from).toList());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PaymentHistory {

        private final Long jobId;
        private final String title;
        private final Long amount;
        private final Long refundAmount;
        private final OffsetDateTime approvedAt;
        private final String studentName;
        private final PaymentHistoryStatus status;
        private final LocalDate settledDate;
        private final LocalDate refundedDate;

        public static PaymentHistory from(PaymentHistoryItemResult result) {
            return new PaymentHistory(result.getJobId(), result.getTitle(), result.getAmount(),
                    result.getRefundAmount(), KoreaTime.from(result.getApprovedAt()), result.getStudentName(),
                    result.getStatus(),
                    result.getSettledDate(), result.getRefundedDate());
        }
    }
}
