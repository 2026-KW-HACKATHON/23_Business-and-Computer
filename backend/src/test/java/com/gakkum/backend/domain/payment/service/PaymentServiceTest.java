package com.gakkum.backend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
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
    private final Job job = Job.builder().id(11L).budget(100_000L).title("포스터 제작").build();
    private final JobApplication application = JobApplication.builder().id(21L).jobId(11L).build();

    @Test
    @DisplayName("서버 금액과 동의 시각으로 고유 PENDING 주문을 저장한다")
    void createsPendingPayment() {
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = service.preparePayment(job, application, USER_ID);
        Payment another = service.preparePayment(job, application, USER_ID);

        assertThat(payment.getJobId()).isEqualTo(11L);
        assertThat(payment.getJobApplicationId()).isEqualTo(21L);
        assertThat(payment.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(payment.getAmount()).isEqualTo(100_000L);
        assertThat(payment.getRefundPolicyAgreedAt()).isEqualTo(NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaymentKey()).isNull();
        assertThat(payment.getApprovedAt()).isNull();
        assertThat(payment.getOrderId()).matches("[0-9a-f-]{36}").isNotEqualTo(another.getOrderId());
    }

    @Test
    @DisplayName("재시도는 기존 PENDING 주문을 먼저 무효화하고 새 주문을 저장한다")
    void supersedesPreviousPendingPayment() {
        Payment previous = Payment.pending(11L, 21L, USER_ID, "old-order", 100_000L, NOW);
        when(repository.findByJobIdAndStatus(11L, PaymentStatus.PENDING)).thenReturn(Optional.of(previous));
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment replacement = service.preparePayment(job, application, USER_ID);

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

        assertThatThrownBy(() -> service.preparePayment(job, application, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_PAID));
        verify(repository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("금액이 양수가 아니면 주문을 만들지 않는다")
    void rejectsNonPositiveAmount() {
        Job zeroBudget = Job.builder().id(11L).budget(0L).build();

        assertThatThrownBy(() -> service.preparePayment(zeroBudget, application, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_AVAILABLE));
        verify(repository, never()).save(any(Payment.class));
    }
}
