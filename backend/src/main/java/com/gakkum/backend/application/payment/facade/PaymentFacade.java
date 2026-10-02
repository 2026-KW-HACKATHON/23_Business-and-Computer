package com.gakkum.backend.application.payment.facade;

import org.springframework.stereotype.Component;
import com.gakkum.backend.application.payment.dto.PaymentPrepareRequest;
import com.gakkum.backend.application.payment.service.PaymentPreparationService;
import com.gakkum.backend.application.payment.service.PaymentApprovalService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.ReadyResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PendingPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PreparePaymentResult;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentPreparationService preparationService;
    private final KakaoPayClient kakaoPayClient;
    private final PaymentService paymentService;
    private final PaymentApprovalService approvalService;

    public ApprovedPaymentData approvePayment(String username, String orderId, String pgToken) {
        return approvalService.approve(username, orderId, pgToken);
    }

    public PreparePaymentResult preparePayment(String username, Long jobId, PaymentPrepareRequest request) {
        PendingPaymentData pending = preparationService.createPending(username, jobId, request);
        ReadyResult ready;
        try {
            ready = kakaoPayClient.ready(pending);
        } catch (BusinessException exception) {
            paymentService.failReady(pending.orderId());
            throw exception;
        }
        paymentService.recordKakaoTid(pending.orderId(), ready.tid());
        return PreparePaymentResult.of(pending.orderId(), pending.amount(), pending.orderName(),
                ready.nextRedirectPcUrl(), ready.nextRedirectMobileUrl());
    }
}
