package com.gakkum.backend.application.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.payment.dto.PaymentPrepareRequest;
import com.gakkum.backend.application.payment.dto.PaymentPrepareResponse;
import com.gakkum.backend.application.payment.dto.ProposalPaymentPrepareRequest;
import com.gakkum.backend.application.payment.dto.PaymentApproveRequest;
import com.gakkum.backend.application.payment.dto.PaymentApproveResponse;
import com.gakkum.backend.application.payment.dto.PaymentHistoryResponse;
import com.gakkum.backend.application.payment.dto.SettlementHistoryResponse;
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

    /** 사장님이 받은 제안을 결제하기 위해 결제창을 준비하는 API. 금액과 학생은 서버가 제안에서 정한다 */
    @PostMapping("/proposals/{proposalId}/payments")
    public ResponseEntity<ApiResponse<PaymentPrepareResponse>> prepareProposalPayment(
            Authentication authentication,
            @PathVariable @Positive Long proposalId,
            @Valid @RequestBody ProposalPaymentPrepareRequest request) {
        PaymentPrepareResponse response = PaymentPrepareResponse.from(
                paymentFacade.prepareProposalPayment(request.toCommand(authentication.getName(), proposalId)));
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

    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<PaymentHistoryResponse>> getPaymentHistory(Authentication authentication) {
        PaymentHistoryResponse response = PaymentHistoryResponse.from(
                paymentFacade.getPaymentHistory(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/settlements")
    public ResponseEntity<ApiResponse<SettlementHistoryResponse>> getSettlementHistory(
            Authentication authentication) {
        SettlementHistoryResponse response = SettlementHistoryResponse.from(
                paymentFacade.getSettlementHistory(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
