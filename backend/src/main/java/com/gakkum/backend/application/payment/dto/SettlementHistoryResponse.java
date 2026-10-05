package com.gakkum.backend.application.payment.dto;

import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryMonthResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistorySummaryResult;
import com.gakkum.backend.domain.payment.dto.SettlementHistoryStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SettlementHistoryResponse {

    private final Summary summary;
    private final List<Month> months;

    public static SettlementHistoryResponse from(SettlementHistoryResult result) {
        return new SettlementHistoryResponse(
                Summary.from(result.getSummary()), result.getMonths().stream().map(Month::from).toList());
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Summary {

        private final Long thisMonthWorkAmount;
        private final Long scheduledAmount;
        private final Long totalSettledAmount;

        public static Summary from(SettlementHistorySummaryResult result) {
            return new Summary(
                    result.getThisMonthWorkAmount(), result.getScheduledAmount(), result.getTotalSettledAmount());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Month {

        private final String yearMonth;
        private final List<Settlement> settlements;

        public static Month from(SettlementHistoryMonthResult result) {
            return new Month(result.getYearMonth(), result.getSettlements().stream().map(Settlement::from).toList());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Settlement {

        private final Long jobId;
        private final String title;
        private final Long amount;
        private final LocalDate settledDate;
        private final String storeName;
        private final SettlementHistoryStatus status;

        public static Settlement from(SettlementHistoryItemResult result) {
            return new Settlement(result.getJobId(), result.getTitle(), result.getAmount(),
                    result.getSettledDate(), result.getStoreName(), result.getStatus());
        }
    }
}
