package com.gakkum.backend.application.payment.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentPrepareRequest {

    @NotNull
    @Positive
    private Long jobApplicationId;

    @NotNull
    @AssertTrue
    private Boolean refundPolicyAgreed;

    public static PaymentPrepareRequest of(Long jobApplicationId, Boolean refundPolicyAgreed) {
        return PaymentPrepareRequest.builder()
                .jobApplicationId(jobApplicationId)
                .refundPolicyAgreed(refundPolicyAgreed)
                .build();
    }
}
