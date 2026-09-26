package com.gakkum.backend.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.PaymentResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedPaymentData;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.domain.payment.repository.PaymentRepository.JobIdProjection;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class PaymentApprovalServiceTest {

    private static final String OWNER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String TID = "T1234567890123456789";
    private static final Instant APPROVED_AT = Instant.parse("2026-09-26T03:00:00Z");

    private final UserService userService = mock(UserService.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final KakaoPayClient kakaoPayClient = mock(KakaoPayClient.class);
    private final PaymentApprovalService service =
            new PaymentApprovalService(userService, jobRepository, jobApplicationRepository,
                    paymentRepository, kakaoPayClient);
    private Job job;
    private JobApplication application;

    private Payment pending() {
        Payment payment = Payment.pending(11L, 21L, OWNER_ID, "order-123", 100_000L, Instant.EPOCH);
        payment.recordKakaoTid(TID);
        return payment;
    }

    private void arrange(Payment payment) {
        job = Job.builder().id(11L).status(JobStatus.OPEN).build();
        application = JobApplication.builder().id(21L).jobId(11L).studentProfileId(31L)
                .status(JobApplicationStatus.PENDING).build();
        when(userService.getActiveUser("KAKAO_123"))
                .thenReturn(User.builder().id(OWNER_ID).role(UserRole.OWNER).build());
        JobIdProjection projection = mock(JobIdProjection.class);
        when(projection.getJobId()).thenReturn(11L);
        when(paymentRepository.findProjectedByOrderId("order-123")).thenReturn(Optional.of(projection));
        when(jobRepository.findLockedById(11L)).thenReturn(Optional.of(job));
        when(paymentRepository.findByOrderId("order-123")).thenReturn(Optional.of(payment));
        when(jobApplicationRepository.findById(21L)).thenReturn(Optional.of(application));
        when(kakaoPayClient.cid()).thenReturn("TC0ONETIME");
    }

    private PaymentResult providerResult(String status) {
        return new PaymentResult(TID, "TC0ONETIME", "order-123", OWNER_ID, 100_000L, status, APPROVED_AT);
    }

    @Test
    @DisplayName("승인 결과가 일치하면 PAID와 선택 지원서 및 의뢰 매칭을 함께 기록한다")
    void approvesMatchingPayment() {
        Payment payment = pending();
        arrange(payment);
        when(kakaoPayClient.order(TID)).thenReturn(providerResult("READY"));
        when(kakaoPayClient.approve(TID, "order-123", OWNER_ID, "pg-123")).thenReturn(providerResult(null));

        ApprovedPaymentData result = service.approve("KAKAO_123", "order-123", "pg-123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getApprovedAt()).isEqualTo(APPROVED_AT);
        assertMatched();
        assertThat(result.amount()).isEqualTo(100_000L);
        InOrder locks = inOrder(jobRepository, paymentRepository);
        locks.verify(paymentRepository).findProjectedByOrderId("order-123");
        locks.verify(jobRepository).findLockedById(11L);
        locks.verify(paymentRepository).findByOrderId("order-123");
    }

    @Test
    @DisplayName("같은 주문의 중복 승인은 카카오페이를 다시 호출하지 않고 저장 결과를 반환한다")
    void duplicateApprovalReturnsStoredResult() {
        Payment payment = pending();
        payment.approve(APPROVED_AT);
        arrange(payment);

        ApprovedPaymentData result = service.approve("KAKAO_123", "order-123", "pg-123");

        assertThat(result.approvedAt()).isEqualTo(APPROVED_AT);
        assertMatched();
        verify(kakaoPayClient, never()).order(TID);
        verify(kakaoPayClient, never()).approve(TID, "order-123", OWNER_ID, "pg-123");
    }

    @Test
    @DisplayName("다른 소유자의 주문은 카카오페이에 보내지 않고 거부한다")
    void rejectsOtherOwner() {
        Payment payment = pending();
        arrange(payment);
        when(userService.getActiveUser("KAKAO_123"))
                .thenReturn(User.builder().id("another-owner").role(UserRole.OWNER).build());

        assertCode(ErrorCode.PAYMENT_FORBIDDEN);
        verify(kakaoPayClient, never()).order(TID);
    }

    @Test
    @DisplayName("SUPERSEDED 주문은 카카오페이에 보내지 않고 거부한다")
    void rejectsSupersededPayment() {
        Payment payment = pending();
        payment.supersede();
        arrange(payment);

        assertCode(ErrorCode.PAYMENT_NOT_AVAILABLE);
        verify(kakaoPayClient, never()).order(TID);
    }

    @Test
    @DisplayName("거래번호가 없는 주문과 준비 실패 주문은 승인하지 않는다")
    void rejectsUnpreparedPayment() {
        Payment payment = Payment.pending(11L, 21L, OWNER_ID, "order-123", 100_000L, Instant.EPOCH);
        arrange(payment);
        assertCode(ErrorCode.PAYMENT_NOT_AVAILABLE);

        payment.failReady();
        assertCode(ErrorCode.PAYMENT_NOT_AVAILABLE);
        verify(kakaoPayClient, never()).order(TID);
    }

    @Test
    @DisplayName("다른 주문으로 이미 결제된 의뢰는 새 주문의 승인을 거부한다")
    void rejectsJobPaidByOtherOrder() {
        arrange(pending());
        when(paymentRepository.existsByJobIdAndStatus(11L, PaymentStatus.PAID)).thenReturn(true);

        assertCode(ErrorCode.PAYMENT_ALREADY_PAID);
        verify(kakaoPayClient, never()).order(TID);
    }

    @Test
    @DisplayName("승인 응답을 놓쳤어도 주문 조회가 성공 상태이면 PAID로 복구한다")
    void reconcilesUncertainApproval() {
        Payment payment = pending();
        arrange(payment);
        when(kakaoPayClient.order(TID)).thenReturn(providerResult("READY"), providerResult("SUCCESS_PAYMENT"));
        when(kakaoPayClient.approve(TID, "order-123", OWNER_ID, "pg-123"))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE));

        service.approve("KAKAO_123", "order-123", "pg-123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertMatched();
    }

    @Test
    @DisplayName("이전 승인 성공을 주문 조회로 확인하면 중복 승인 요청을 보내지 않는다")
    void reconcilesBeforeApprovalRequest() {
        Payment payment = pending();
        arrange(payment);
        when(kakaoPayClient.order(TID)).thenReturn(providerResult("SUCCESS_PAYMENT"));

        service.approve("KAKAO_123", "order-123", "pg-123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertMatched();
        verify(kakaoPayClient, never()).approve(TID, "order-123", OWNER_ID, "pg-123");
    }

    @Test
    @DisplayName("이미 매칭된 PAID 주문은 상태를 유지하고 카카오페이를 다시 호출하지 않는다")
    void repeatedPaidApprovalIsIdempotent() {
        Payment payment = pending();
        payment.approve(APPROVED_AT);
        arrange(payment);
        job.match(31L);
        application.accept();

        service.approve("KAKAO_123", "order-123", "pg-123");

        assertMatched();
        verify(kakaoPayClient, never()).order(TID);
    }

    @Test
    @DisplayName("승인 전에 지원서가 더 이상 대기 상태가 아니면 카카오페이를 호출하지 않는다")
    void rejectsUnavailableApplicationBeforeProviderCall() {
        arrange(pending());
        application = JobApplication.builder().id(21L).jobId(11L).studentProfileId(31L)
                .status(JobApplicationStatus.REJECTED).build();
        when(jobApplicationRepository.findById(21L)).thenReturn(Optional.of(application));

        assertCode(ErrorCode.PAYMENT_NOT_AVAILABLE);
        verify(kakaoPayClient, never()).order(TID);
    }

    @Test
    @DisplayName("승인 전에 의뢰가 이미 다른 학생과 매칭되면 카카오페이를 호출하지 않는다")
    void rejectsAlreadyMatchedJobBeforeProviderCall() {
        arrange(pending());
        job.match(99L);

        assertCode(ErrorCode.PAYMENT_NOT_AVAILABLE);
        verify(kakaoPayClient, never()).order(TID);
    }

    @Test
    @DisplayName("주문의 지원서가 다른 의뢰에 속하면 카카오페이를 호출하지 않는다")
    void rejectsApplicationFromOtherJob() {
        arrange(pending());
        application = JobApplication.builder().id(21L).jobId(12L).studentProfileId(31L)
                .status(JobApplicationStatus.PENDING).build();
        when(jobApplicationRepository.findById(21L)).thenReturn(Optional.of(application));

        assertCode(ErrorCode.PAYMENT_NOT_AVAILABLE);
        verify(kakaoPayClient, never()).order(TID);
    }

    @Test
    @DisplayName("카카오페이의 주문번호나 결제 금액이 다르면 PAID로 변경하지 않는다")
    void rejectsProviderMismatch() {
        Payment payment = pending();
        arrange(payment);
        when(kakaoPayClient.order(TID)).thenReturn(
                new PaymentResult(TID, "TC0ONETIME", "wrong-order", OWNER_ID, 100_000L, "READY", null));

        assertCode(ErrorCode.PAYMENT_RESULT_MISMATCH);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertUnmatched();
        verify(kakaoPayClient, never()).approve(TID, "order-123", OWNER_ID, "pg-123");
    }

    @Test
    @DisplayName("승인 응답의 금액이 저장 금액과 다르면 PAID로 변경하지 않는다")
    void rejectsApprovalAmountMismatch() {
        Payment payment = pending();
        arrange(payment);
        when(kakaoPayClient.order(TID)).thenReturn(providerResult("READY"));
        when(kakaoPayClient.approve(TID, "order-123", OWNER_ID, "pg-123"))
                .thenReturn(new PaymentResult(TID, "TC0ONETIME", "order-123", OWNER_ID,
                        99_999L, null, APPROVED_AT));

        assertCode(ErrorCode.PAYMENT_RESULT_MISMATCH);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertUnmatched();
    }

    @Test
    @DisplayName("카카오페이가 실패로 기록한 주문에는 승인 요청을 보내지 않는다")
    void rejectsFailedProviderOrder() {
        arrange(pending());
        when(kakaoPayClient.order(TID)).thenReturn(providerResult("FAIL_PAYMENT"));

        assertCode(ErrorCode.PAYMENT_NOT_AVAILABLE);
        verify(kakaoPayClient, never()).approve(TID, "order-123", OWNER_ID, "pg-123");
    }

    @Test
    @DisplayName("승인 결과를 계속 확인할 수 없으면 PENDING으로 남기고 재시도 오류를 반환한다")
    void leavesUnknownResultPending() {
        Payment payment = pending();
        arrange(payment);
        when(kakaoPayClient.order(TID)).thenReturn(providerResult("READY"), providerResult("READY"));
        when(kakaoPayClient.approve(TID, "order-123", OWNER_ID, "pg-123"))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE));

        assertCode(ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertUnmatched();
    }

    private void assertMatched() {
        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(job.getSelectedStudentProfileId()).isEqualTo(31L);
        assertThat(application.getStatus()).isEqualTo(JobApplicationStatus.ACCEPTED);
    }

    private void assertUnmatched() {
        assertThat(job.getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(job.getSelectedStudentProfileId()).isNull();
        assertThat(application.getStatus()).isEqualTo(JobApplicationStatus.PENDING);
    }

    private void assertCode(ErrorCode expected) {
        assertThatThrownBy(() -> service.approve("KAKAO_123", "order-123", "pg-123"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
