package com.gakkum.backend.application.payment.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gakkum.backend.application.job.dto.JobListResponse.SpecialtyCategory;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryDetailResult;
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

    public static SettlementHistoryResponse from(SettlementHistoryDetailResult result) {
        SettlementHistoryResult history = result.getHistory();
        return new SettlementHistoryResponse(Summary.from(history.getSummary()),
                history.getMonths().stream().map(month -> Month.of(month, result)).toList());
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

        public static Month of(SettlementHistoryMonthResult result, SettlementHistoryDetailResult detail) {
            return new Month(result.getYearMonth(), result.getSettlements().stream()
                    .map(item -> Settlement.of(item,
                            detail.getSpecialtyCategoriesByJobId().getOrDefault(item.getJobId(), List.of()),
                            detail.getRatingsByJobId().get(item.getJobId())))
                    .toList());
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
        // 의뢰 분야. 정산 내역 API에서만 내리고, 같은 줄을 쓰는 내 정보 응답에서는 생략한다
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<SpecialtyCategory> specialtyCategories;
        // 학생이 받은 후기 별점. 후기가 없거나 정산 내역 API가 아니면 생략한다
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Integer rating;

        public static Settlement from(SettlementHistoryItemResult result) {
            return new Settlement(result.getJobId(), result.getTitle(), result.getAmount(),
                    result.getSettledDate(), result.getStoreName(), result.getStatus(), null, null);
        }

        public static Settlement of(SettlementHistoryItemResult result,
                List<SpecialtyCategoryResult> specialtyCategories, Integer rating) {
            return new Settlement(result.getJobId(), result.getTitle(), result.getAmount(),
                    result.getSettledDate(), result.getStoreName(), result.getStatus(),
                    specialtyCategories.stream().map(SpecialtyCategory::from).toList(), rating);
        }
    }
}
