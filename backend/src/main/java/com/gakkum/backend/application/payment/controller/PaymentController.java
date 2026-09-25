package com.gakkum.backend.application.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.payment.dto.PaymentPrepareRequest;
import com.gakkum.backend.application.payment.dto.PaymentPrepareResponse;
import com.gakkum.backend.application.payment.dto.PaymentApproveRequest;
import com.gakkum.backend.application.payment.dto.PaymentApproveResponse;
import com.gakkum.backend.application.payment.facade.PaymentFacade;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacade paymentFacade;

    @PostMapping("/jobs/{jobId}/payments")
    public ResponseEntity<ApiResponse<PaymentPrepareResponse>> preparePayment(
            Authentication authentication,
            @PathVariable @Positive Long jobId,
            @Valid @RequestBody PaymentPrepareRequest request) {
        PaymentPrepareResponse response = PaymentPrepareResponse.from(
                paymentFacade.preparePayment(authentication.getName(), jobId, request));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/payments/{orderId}/approve")
    public ResponseEntity<ApiResponse<PaymentApproveResponse>> approvePayment(
            Authentication authentication,
            @PathVariable String orderId,
            @Valid @RequestBody PaymentApproveRequest request) {
        PaymentApproveResponse response = PaymentApproveResponse.from(
                paymentFacade.approvePayment(authentication.getName(), orderId, request.getPgToken()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
