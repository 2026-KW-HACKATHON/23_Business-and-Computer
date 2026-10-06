package com.gakkum.backend.application.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.gakkum.backend.application.payment.dto.PaymentPrepareRequest;
import com.gakkum.backend.application.payment.service.PaymentPreparationService;
import com.gakkum.backend.application.payment.service.PaymentApprovalService;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.ReadyResult;
import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PrepareProposalPaymentCommand;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PendingPaymentData;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PreparePaymentResult;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class PaymentFacadeTest {

    private final PaymentPreparationService preparationService = mock(PaymentPreparationService.class);
    private final KakaoPayClient kakaoPayClient = mock(KakaoPayClient.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentApprovalService approvalService = mock(PaymentApprovalService.class);
    private final PaymentFacade facade = new PaymentFacade(preparationService, kakaoPayClient, paymentService,
            approvalService, mock(UserService.class), mock(JobService.class), mock(StudentService.class),
            mock(OwnerService.class), Clock.systemUTC());
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

    private final PrepareProposalPaymentCommand proposalCommand =
            PrepareProposalPaymentCommand.of("KAKAO_123", 5L, 120_000L, 2, null);

    @Test
    @DisplayName("제안 결제 준비는 사장님이 입력해 주문에 저장된 금액으로 카카오페이 결제창을 만들고 같은 금액을 반환한다")
    void preparesProposalPaymentWithOrderAmount() {
        PendingPaymentData proposalPending =
                new PendingPaymentData("order-456", 120_000L, "메뉴판 개선 제안", "owner-123");
        when(preparationService.createPendingForProposal(proposalCommand)).thenReturn(proposalPending);
        when(kakaoPayClient.ready(proposalPending)).thenReturn(
                new ReadyResult("T1234567890123456789", "https://pay.example/pc", "https://pay.example/mobile"));

        PreparePaymentResult result = facade.prepareProposalPayment(proposalCommand);

        assertThat(result.getOrderId()).isEqualTo("order-456");
        assertThat(result.getAmount()).isEqualTo(120_000L);
        ArgumentCaptor<PendingPaymentData> readyRequest = ArgumentCaptor.forClass(PendingPaymentData.class);
        verify(kakaoPayClient).ready(readyRequest.capture());
        assertThat(readyRequest.getValue().amount()).isEqualTo(120_000L);
        verify(paymentService).recordKakaoTid("order-456", "T1234567890123456789");
    }

    private Payment stuckOrder() {
        Payment payment = Payment.pendingForProposal(5L, "owner-123", "old-order", 50_000L, 1, null, Instant.EPOCH);
        payment.recordKakaoTid("T0000000000000000001");
        return payment;
    }

    private void assertPrepareRejected(ErrorCode errorCode) {
        assertThatThrownBy(() -> facade.prepareProposalPayment(proposalCommand))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    @Test
    @DisplayName("제안 결제 준비가 이미 결제됨으로 거부되면 PENDING으로 남은 주문을 복구하고 결제창을 만들지 않은 채 409를 그대로 반환한다")
    void recoversStuckOrderWhenProposalAlreadyPaid() {
        when(preparationService.createPendingForProposal(proposalCommand))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID));
        when(paymentService.findPendingProposalPayment(5L)).thenReturn(Optional.of(stuckOrder()));

        assertPrepareRejected(ErrorCode.PAYMENT_ALREADY_PAID);

        verify(approvalService).recoverPaidOrder("KAKAO_123", "old-order");
        verifyNoInteractions(kakaoPayClient);
    }

    @Test
    @DisplayName("일반 의뢰 결제 준비가 이미 결제됨으로 거부돼도 PENDING으로 남은 주문을 복구한다")
    void recoversStuckOrderWhenJobAlreadyPaid() {
        Payment stuck = Payment.pending(11L, 21L, "owner-123", "old-order", 100_000L, Instant.EPOCH);
        when(preparationService.createPending("KAKAO_123", 11L, request))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID));
        when(paymentService.findPendingPayment(11L)).thenReturn(Optional.of(stuck));

        assertThatThrownBy(() -> facade.preparePayment("KAKAO_123", 11L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_PAID));

        verify(approvalService).recoverPaidOrder("KAKAO_123", "old-order");
    }

    @Test
    @DisplayName("이미 기록된 결제라 PENDING 주문이 없거나 복구가 실패해도 원래의 409를 그대로 반환한다")
    void keepsOriginalRejectionWhenNothingToRecoverOrRecoveryFails() {
        when(preparationService.createPendingForProposal(proposalCommand))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID));
        when(paymentService.findPendingProposalPayment(5L)).thenReturn(Optional.empty());
        assertPrepareRejected(ErrorCode.PAYMENT_ALREADY_PAID);
        verifyNoInteractions(approvalService);

        when(paymentService.findPendingProposalPayment(5L)).thenReturn(Optional.of(stuckOrder()));
        when(approvalService.recoverPaidOrder("KAKAO_123", "old-order"))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE));
        assertPrepareRejected(ErrorCode.PAYMENT_ALREADY_PAID);
    }

    @Test
    @DisplayName("다른 이유로 거부된 결제 준비는 주문 복구를 시도하지 않는다")
    void doesNotRecoverOnOtherRejections() {
        when(preparationService.createPendingForProposal(proposalCommand))
                .thenThrow(new BusinessException(ErrorCode.PROPOSAL_PAYMENT_NOT_AVAILABLE));

        assertPrepareRejected(ErrorCode.PROPOSAL_PAYMENT_NOT_AVAILABLE);

        verify(paymentService, never()).findPendingProposalPayment(5L);
        verifyNoInteractions(approvalService);
    }
}
