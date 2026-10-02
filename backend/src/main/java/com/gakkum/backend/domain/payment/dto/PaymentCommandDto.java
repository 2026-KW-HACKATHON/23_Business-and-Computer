package com.gakkum.backend.domain.payment.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class PaymentCommandDto {

    private PaymentCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PreparePaymentCommand {

        private final Long jobId;
        private final Long jobApplicationId;
        private final String ownerUserId;
        private final Long amount;

        public static PreparePaymentCommand of(Long jobId, Long jobApplicationId, String ownerUserId, Long amount) {
            return PreparePaymentCommand.builder()
                    .jobId(jobId)
                    .jobApplicationId(jobApplicationId)
                    .ownerUserId(ownerUserId)
                    .amount(amount)
                    .build();
        }
    }
}
