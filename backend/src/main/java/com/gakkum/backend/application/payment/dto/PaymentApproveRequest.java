package com.gakkum.backend.application.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentApproveRequest(@NotBlank String pgToken) {
}
