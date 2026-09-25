package com.gakkum.backend.application.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.gakkum.backend.application.payment.dto.PaymentPrepareRequest;
import com.gakkum.backend.application.payment.service.PaymentPreparationService;
import com.gakkum.backend.application.payment.service.PaymentApprovalService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.ReadyResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PendingPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PreparePaymentResult;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class PaymentFacadeTest {

    private final PaymentPreparationService preparationService = mock(PaymentPreparationService.class);
    private final KakaoPayClient kakaoPayClient = mock(KakaoPayClient.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentApprovalService approvalService = mock(PaymentApprovalService.class);
    private final PaymentFacade facade = new PaymentFacade(preparationService, kakaoPayClient, paymentService, approvalService);
    private final PaymentPrepareRequest request = PaymentPrepareRequest.of(21L, true);
    private final PendingPaymentData pending = new PendingPaymentData("order-123", 100_000L, "포스터 제작", "owner-123");

    @Test
    @DisplayName("결제 생성과 카카오페이 준비 후 거래번호를 저장하고 PC 및 모바일 URL을 반환한다")
    void preparesPayment() {
        when(preparationService.createPending("KAKAO_123", 11L, request)).thenReturn(pending);
        when(kakaoPayClient.ready(pending)).thenReturn(
                new ReadyResult("T1234567890123456789", "https://pay.example/pc", "https://pay.example/mobile"));

        PreparePaymentResult result = facade.preparePayment("KAKAO_123", 11L, request);

        assertThat(result.getOrderId()).isEqualTo("order-123");
        assertThat(result.getAmount()).isEqualTo(100_000L);
        assertThat(result.getOrderName()).isEqualTo("포스터 제작");
        assertThat(result.getNextRedirectPcUrl()).isEqualTo("https://pay.example/pc");
        assertThat(result.getNextRedirectMobileUrl()).isEqualTo("https://pay.example/mobile");
        InOrder order = inOrder(preparationService, kakaoPayClient, paymentService);
        order.verify(preparationService).createPending("KAKAO_123", 11L, request);
        order.verify(kakaoPayClient).ready(pending);
        order.verify(paymentService).recordKakaoTid("order-123", "T1234567890123456789");
    }

    @Test
    @DisplayName("카카오페이 준비 실패 시 해당 주문을 실패 처리하고 URL을 반환하지 않는다")
    void failsReady() {
        when(preparationService.createPending("KAKAO_123", 11L, request)).thenReturn(pending);
        when(kakaoPayClient.ready(pending)).thenThrow(new BusinessException(ErrorCode.PAYMENT_READY_FAILED));

        assertThatThrownBy(() -> facade.preparePayment("KAKAO_123", 11L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_READY_FAILED));
        verify(paymentService).failReady("order-123");
        verify(paymentService, never()).recordKakaoTid("order-123", "T1234567890123456789");
    }

    @Test
    @DisplayName("무효화된 주문에 거래번호 저장이 거절되면 URL을 반환하지 않는다")
    void doesNotReturnSupersededReady() {
        when(preparationService.createPending("KAKAO_123", 11L, request)).thenReturn(pending);
        when(kakaoPayClient.ready(pending)).thenReturn(
                new ReadyResult("T1234567890123456789", "https://pay.example/pc", "https://pay.example/mobile"));
        doThrow(new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE))
                .when(paymentService).recordKakaoTid("order-123", "T1234567890123456789");

        assertThatThrownBy(() -> facade.preparePayment("KAKAO_123", 11L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_AVAILABLE));
    }
}
