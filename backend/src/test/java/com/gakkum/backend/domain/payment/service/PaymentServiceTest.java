package com.gakkum.backend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PreparePaymentCommand;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.RefundedPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryData;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class PaymentServiceTest {

    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final PaymentService service = new PaymentService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    private final PreparePaymentCommand command = PreparePaymentCommand.of(11L, 21L, USER_ID, 100_000L);

    @Test
    @DisplayName("서버 금액과 동의 시각으로 고유 PENDING 주문을 저장한다")
    void createsPendingPayment() {
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = service.preparePayment(command);
        Payment another = service.preparePayment(command);

        assertThat(payment.getJobId()).isEqualTo(11L);
        assertThat(payment.getJobApplicationId()).isEqualTo(21L);
        assertThat(payment.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(payment.getAmount()).isEqualTo(100_000L);
        assertThat(payment.getRefundPolicyAgreedAt()).isEqualTo(NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaymentKey()).isNull();
        assertThat(payment.getKakaoTid()).isNull();
        assertThat(payment.getApprovedAt()).isNull();
        assertThat(payment.getOrderId()).matches("[0-9a-f-]{36}").isNotEqualTo(another.getOrderId());
    }

    @Test
    @DisplayName("재시도는 기존 PENDING 주문을 먼저 무효화하고 새 주문을 저장한다")
    void supersedesPreviousPendingPayment() {
        Payment previous = Payment.pending(11L, 21L, USER_ID, "old-order", 100_000L, NOW);
        when(repository.findByJobIdAndStatus(11L, PaymentStatus.PENDING)).thenReturn(Optional.of(previous));
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment replacement = service.preparePayment(command);

        assertThat(previous.getStatus()).isEqualTo(PaymentStatus.SUPERSEDED);
        assertThat(replacement.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(replacement.getOrderId()).isNotEqualTo(previous.getOrderId());
        InOrder order = inOrder(repository);
        order.verify(repository).flush();
        order.verify(repository).save(replacement);
    }

    @Test
    @DisplayName("이미 결제된 의뢰는 새 주문을 만들지 않는다")
    void rejectsPaidJob() {
        when(repository.existsByJobIdAndStatus(11L, PaymentStatus.PAID)).thenReturn(true);

        assertThatThrownBy(() -> service.preparePayment(command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_PAID));
        verify(repository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("금액이 없거나 양수가 아니면 주문을 만들지 않는다")
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> service.preparePayment(PreparePaymentCommand.of(11L, 21L, USER_ID, 0L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_AVAILABLE));
        assertThatThrownBy(() -> service.preparePayment(PreparePaymentCommand.of(11L, 21L, USER_ID, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_AVAILABLE));
        verify(repository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("PENDING 주문에만 카카오페이 거래번호를 기록한다")
    void recordsKakaoTidOnlyForPending() {
        Payment pending = Payment.pending(11L, 21L, USER_ID, "order-123", 100_000L, NOW);
        when(repository.findByOrderId("order-123")).thenReturn(Optional.of(pending));

        service.recordKakaoTid("order-123", "T1234567890123456789");

        assertThat(pending.getKakaoTid()).isEqualTo("T1234567890123456789");
        assertThatThrownBy(() -> service.recordKakaoTid("order-123", "T9876543210987654321"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_AVAILABLE));
    }

    @Test
    @DisplayName("더 새 결제가 무효화한 주문은 늦게 도착한 거래번호를 기록하지 않는다")
    void rejectsSupersededReady() {
        Payment previous = Payment.pending(11L, 21L, USER_ID, "old-order", 100_000L, NOW);
        previous.supersede();
        when(repository.findByOrderId("old-order")).thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> service.recordKakaoTid("old-order", "T1234567890123456789"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_AVAILABLE));
        assertThat(previous.getKakaoTid()).isNull();
        service.failReady("old-order");
        assertThat(previous.getStatus()).isEqualTo(PaymentStatus.SUPERSEDED);
    }

    @Test
    @DisplayName("카카오페이 준비 실패 시 현재 주문을 READY_FAILED로 기록한다")
    void marksReadyFailed() {
        Payment pending = Payment.pending(11L, 21L, USER_ID, "order-123", 100_000L, NOW);
        when(repository.findByOrderId("order-123")).thenReturn(Optional.of(pending));

        service.failReady("order-123");

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.READY_FAILED);
    }

    @Test
    @DisplayName("의뢰 취소 시 결제 완료 주문을 REFUNDED로 바꾸고 학생 보상금 20%를 뺀 환불 금액을 기록한다")
    void refundsPaidPaymentOnCancel() {
        Payment paid = Payment.pending(11L, 21L, USER_ID, "order-123", 100_000L, NOW);
        paid.recordKakaoTid("T1234567890123456789");
        paid.approve(NOW);
        when(repository.findByJobIdAndStatus(11L, PaymentStatus.PAID)).thenReturn(Optional.of(paid));

        RefundedPaymentData result = service.refundOnCancel(11L);

        assertThat(result).isEqualTo(new RefundedPaymentData(100_000L, 20_000L, 80_000L, NOW));
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("취소할 진행 중 의뢰에 결제 완료 주문이 없으면 데이터 무결성 오류로 처리한다")
    void rejectsRefundWithoutPaidPayment() {
        when(repository.findByJobIdAndStatus(11L, PaymentStatus.PAID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refundOnCancel(11L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Test
    @DisplayName("결제 내역은 본인의 PAID·REFUNDED 결제만 저장소 정렬 순서대로 반환한다")
    void returnsPaymentHistoryInRepositoryOrder() {
        Payment refunded = historyPayment(12L, "order-refunded", "T0000000000000000002");
        refunded.approve(NOW);
        refunded.refundOnCancel(NOW.plusSeconds(60));
        Payment paid = historyPayment(11L, "order-paid", "T0000000000000000001");
        paid.approve(NOW.minusSeconds(60));
        when(repository.findByOwnerUserIdAndStatusInOrderByApprovedAtDescIdDesc(
                USER_ID, List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED))).thenReturn(List.of(refunded, paid));

        List<PaymentHistoryData> history = service.getPaymentHistory(USER_ID);

        assertThat(history).extracting(PaymentHistoryData::getJobId).containsExactly(12L, 11L);
        assertThat(history.get(0).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(history.get(0).getAmount()).isEqualTo(100_000L);
        assertThat(history.get(0).getRefundAmount()).isEqualTo(80_000L);
        assertThat(history.get(0).getApprovedAt()).isEqualTo(NOW);
        assertThat(history.get(1).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(history.get(1).getJobApplicationId()).isEqualTo(21L);
        assertThat(history.get(1).getRefundAmount()).isNull();
    }

    @Test
    @DisplayName("결제 내역에 승인 시각이 없는 결제가 있으면 누락하지 않고 서버 오류로 처리한다")
    void rejectsPaymentHistoryWithoutApprovedAt() {
        Payment broken = mock(Payment.class);
        when(broken.getApprovedAt()).thenReturn(null);
        when(repository.findByOwnerUserIdAndStatusInOrderByApprovedAtDescIdDesc(
                USER_ID, List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED))).thenReturn(List.of(broken));

        assertThatThrownBy(() -> service.getPaymentHistory(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Test
    @DisplayName("정산 내역은 지원서 ID에 연결된 PAID·REFUNDED 결제만 저장소 정렬 순서대로 반환한다")
    void returnsSettlementHistoryInRepositoryOrder() {
        Payment refunded = historyPayment(12L, "order-refunded", "T0000000000000000002");
        refunded.approve(NOW);
        refunded.refundOnCancel(NOW.plusSeconds(60));
        Payment paid = historyPayment(11L, "order-paid", "T0000000000000000001");
        paid.approve(NOW.minusSeconds(60));
        when(repository.findByJobApplicationIdInAndStatusInOrderByApprovedAtDescIdDesc(
                List.of(21L, 22L), List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED)))
                .thenReturn(List.of(refunded, paid));

        List<SettlementHistoryData> history = service.getSettlementHistory(List.of(21L, 22L));

        assertThat(history).extracting(SettlementHistoryData::getJobId).containsExactly(12L, 11L);
        assertThat(history.get(0).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(history.get(0).getAmount()).isEqualTo(100_000L);
        assertThat(history.get(0).getStudentCompensationAmount()).isEqualTo(20_000L);
        assertThat(history.get(0).getApprovedAt()).isEqualTo(NOW);
        assertThat(history.get(0).getRefundedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(history.get(1).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(history.get(1).getJobApplicationId()).isEqualTo(21L);
        assertThat(history.get(1).getStudentCompensationAmount()).isNull();
        assertThat(history.get(1).getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("지원서가 없으면 저장소를 조회하지 않고 빈 정산 내역을 반환한다")
    void returnsEmptySettlementHistoryWithoutApplications() {
        assertThat(service.getSettlementHistory(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("정산 내역에 승인 시각이 없는 결제가 있으면 누락하지 않고 서버 오류로 처리한다")
    void rejectsSettlementHistoryWithoutApprovedAt() {
        Payment broken = mock(Payment.class);
        when(broken.getApprovedAt()).thenReturn(null);
        when(repository.findByJobApplicationIdInAndStatusInOrderByApprovedAtDescIdDesc(
                List.of(21L), List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED))).thenReturn(List.of(broken));

        assertThatThrownBy(() -> service.getSettlementHistory(List.of(21L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private Payment historyPayment(Long jobId, String orderId, String tid) {
        Payment payment = Payment.pending(jobId, 21L, USER_ID, orderId, 100_000L, NOW);
        payment.recordKakaoTid(tid);
        return payment;
    }
}
