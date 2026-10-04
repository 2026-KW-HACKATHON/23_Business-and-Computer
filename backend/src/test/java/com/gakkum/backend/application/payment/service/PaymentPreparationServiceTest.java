package com.gakkum.backend.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.application.payment.dto.PaymentPrepareRequest;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.PaymentResult;
import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PreparePaymentCommand;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PendingPaymentData;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PrepareProposalPaymentCommand;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class PaymentPreparationServiceTest {

    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";

    private final UserService userService = mock(UserService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final JobService jobService = mock(JobService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final ProposalService proposalService = mock(ProposalService.class);
    private final KakaoPayClient kakaoPayClient = mock(KakaoPayClient.class);
    private final PaymentPreparationService service =
            new PaymentPreparationService(userService, ownerService, jobService, paymentService, proposalService,
                    kakaoPayClient);

    @Test
    @DisplayName("사장님의 의뢰와 지원서를 확인하고 서버 금액으로 결제 시도를 만든다")
    void createsPendingPayment() {
        User user = User.builder().id(USER_ID).role(UserRole.OWNER).build();
        Owner owner = Owner.builder().id(7L).build();
        Job job = Job.builder().id(11L).title("포스터 제작").budget(100_000L).build();
        JobApplication application = JobApplication.builder().id(21L).jobId(11L).build();
        Payment payment = Payment.pending(11L, 21L, USER_ID, "order-123", 100_000L, Instant.EPOCH);
        when(userService.getActiveUser("KAKAO_123")).thenReturn(user);
        when(ownerService.getOwnerProfile(USER_ID)).thenReturn(owner);
        when(jobService.getPayableJobForUpdate(11L, 7L)).thenReturn(job);
        when(jobService.getPayableApplication(11L, 21L)).thenReturn(application);
        when(paymentService.preparePayment(any(PreparePaymentCommand.class))).thenReturn(payment);

        PendingPaymentData result = service.createPending("KAKAO_123", 11L, PaymentPrepareRequest.of(21L, true));

        assertThat(result.orderId()).isEqualTo("order-123");
        assertThat(result.amount()).isEqualTo(100_000L);
        assertThat(result.orderName()).isEqualTo("포스터 제작");
        assertThat(result.ownerUserId()).isEqualTo(USER_ID);
        ArgumentCaptor<PreparePaymentCommand> command = ArgumentCaptor.forClass(PreparePaymentCommand.class);
        verify(paymentService).preparePayment(command.capture());
        assertThat(command.getValue().getJobId()).isEqualTo(11L);
        assertThat(command.getValue().getJobApplicationId()).isEqualTo(21L);
        assertThat(command.getValue().getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(command.getValue().getAmount()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("제안을 받은 사장님의 제안을 잠가 확인하고 요청 값이 아닌 제안의 작업비와 타이틀로 결제 시도를 만든다")
    void createsPendingProposalPayment() {
        User user = User.builder().id(USER_ID).role(UserRole.OWNER).build();
        Proposal proposal = Proposal.builder().id(5L).ownerProfileId(7L).studentProfileId(31L)
                .title("메뉴판 개선 제안").proposedFee(50_000L).build();
        Payment payment = Payment.pendingForProposal(5L, USER_ID, "order-123", 50_000L, 2, "잘 부탁드립니다.",
                Instant.EPOCH);
        when(userService.getActiveUser("KAKAO_123")).thenReturn(user);
        when(ownerService.getOwnerProfile(USER_ID)).thenReturn(Owner.builder().id(7L).build());
        when(proposalService.getPayableProposalForUpdate(5L, 7L)).thenReturn(proposal);
        when(paymentService.prepareProposalPayment(5L, USER_ID, 50_000L, 2, "잘 부탁드립니다.")).thenReturn(payment);

        PendingPaymentData result = service.createPendingForProposal(
                PrepareProposalPaymentCommand.of("KAKAO_123", 5L, 2, "잘 부탁드립니다."));

        assertThat(result.orderId()).isEqualTo("order-123");
        assertThat(result.amount()).isEqualTo(50_000L);
        assertThat(result.orderName()).isEqualTo("메뉴판 개선 제안");
        assertThat(result.ownerUserId()).isEqualTo(USER_ID);
        verify(paymentService).prepareProposalPayment(5L, USER_ID, 50_000L, 2, "잘 부탁드립니다.");
        verifyNoInteractions(jobService);
    }

    @Test
    @DisplayName("사장님 계정이 아니면 제안을 조회하지 않고 결제 시도를 만들지 않는다")
    void rejectsNonOwnerForProposalPayment() {
        when(userService.getActiveUser("KAKAO_123"))
                .thenReturn(User.builder().id(USER_ID).role(UserRole.STUDENT).build());

        assertThatThrownBy(() -> service.createPendingForProposal(
                PrepareProposalPaymentCommand.of("KAKAO_123", 5L, 2, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_OWNER_REQUIRED));
        verifyNoInteractions(ownerService, proposalService, paymentService);
    }

    @Test
    @DisplayName("다른 사장님이 받았거나 결제할 수 없는 제안이면 결제 시도를 만들지 않는다")
    void rejectsUnpayableProposal() {
        when(userService.getActiveUser("KAKAO_123"))
                .thenReturn(User.builder().id(USER_ID).role(UserRole.OWNER).build());
        when(ownerService.getOwnerProfile(USER_ID)).thenReturn(Owner.builder().id(7L).build());
        when(proposalService.getPayableProposalForUpdate(5L, 7L))
                .thenThrow(new BusinessException(ErrorCode.PROPOSAL_PAYMENT_FORBIDDEN));

        assertThatThrownBy(() -> service.createPendingForProposal(
                PrepareProposalPaymentCommand.of("KAKAO_123", 5L, 2, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_PAYMENT_FORBIDDEN));
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("사장님 계정이 아니면 결제 시도를 만들지 않는다")
    void rejectsNonOwner() {
        when(userService.getActiveUser("KAKAO_123"))
                .thenReturn(User.builder().id(USER_ID).role(UserRole.STUDENT).build());

        assertThatThrownBy(() -> service.createPending("KAKAO_123", 11L, PaymentPrepareRequest.of(21L, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_OWNER_REQUIRED));
        verifyNoInteractions(ownerService, jobService, paymentService);
    }

    private static final String PREVIOUS_TID = "T0000000000000000001";

    private Payment previousProposalOrder(String tid) {
        Payment previous = Payment.pendingForProposal(5L, USER_ID, "old-order", 50_000L, 1, null, Instant.EPOCH);
        if (tid != null) {
            previous.recordKakaoTid(tid);
        }
        return previous;
    }

    private void givenPayableProposal(Payment previous) {
        when(userService.getActiveUser("KAKAO_123"))
                .thenReturn(User.builder().id(USER_ID).role(UserRole.OWNER).build());
        when(ownerService.getOwnerProfile(USER_ID)).thenReturn(Owner.builder().id(7L).build());
        when(proposalService.getPayableProposalForUpdate(5L, 7L)).thenReturn(Proposal.builder()
                .id(5L).ownerProfileId(7L).title("메뉴판 개선 제안").proposedFee(50_000L).build());
        when(paymentService.findPendingProposalPayment(5L)).thenReturn(Optional.ofNullable(previous));
        when(paymentService.prepareProposalPayment(5L, USER_ID, 50_000L, 2, null)).thenReturn(
                Payment.pendingForProposal(5L, USER_ID, "order-123", 50_000L, 2, null, Instant.EPOCH));
    }

    private PaymentResult providerOrder(String status) {
        return new PaymentResult(PREVIOUS_TID, "TC0ONETIME", "old-order", USER_ID, 50_000L, status, Instant.EPOCH);
    }

    @Test
    @DisplayName("이전 대기 주문이 카카오페이에서 이미 결제됐으면 대체하지 않고 PAYMENT_409_PAID로 거부한다")
    void rejectsReprepareWhenPreviousOrderIsPaidAtProvider() {
        givenPayableProposal(previousProposalOrder(PREVIOUS_TID));
        when(kakaoPayClient.order(PREVIOUS_TID)).thenReturn(providerOrder("SUCCESS_PAYMENT"));

        assertThatThrownBy(() -> service.createPendingForProposal(
                PrepareProposalPaymentCommand.of("KAKAO_123", 5L, 2, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_PAID));
        verify(paymentService, never()).prepareProposalPayment(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("이전 대기 주문의 결제 여부를 조회하지 못하면 대체하지 않고 조회 오류를 그대로 반환한다")
    void doesNotSupersedeWhenProviderStatusIsUnknown() {
        givenPayableProposal(previousProposalOrder(PREVIOUS_TID));
        when(kakaoPayClient.order(PREVIOUS_TID))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE));

        assertThatThrownBy(() -> service.createPendingForProposal(
                PrepareProposalPaymentCommand.of("KAKAO_123", 5L, 2, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE));
        verify(paymentService, never()).prepareProposalPayment(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("이전 대기 주문이 아직 결제되지 않았으면 새 주문을 만든다")
    void repreparesWhenPreviousOrderIsUnpaid() {
        givenPayableProposal(previousProposalOrder(PREVIOUS_TID));
        when(kakaoPayClient.order(PREVIOUS_TID)).thenReturn(providerOrder("READY"));

        PendingPaymentData result = service.createPendingForProposal(
                PrepareProposalPaymentCommand.of("KAKAO_123", 5L, 2, null));

        assertThat(result.orderId()).isEqualTo("order-123");
    }

    @Test
    @DisplayName("이전 대기 주문이 없거나 거래번호가 없으면 카카오페이를 조회하지 않고 새 주문을 만든다")
    void skipsProviderCheckWithoutPreviousTid() {
        givenPayableProposal(null);
        service.createPendingForProposal(PrepareProposalPaymentCommand.of("KAKAO_123", 5L, 2, null));

        givenPayableProposal(previousProposalOrder(null));
        service.createPendingForProposal(PrepareProposalPaymentCommand.of("KAKAO_123", 5L, 2, null));

        verifyNoInteractions(kakaoPayClient);
        verify(paymentService, org.mockito.Mockito.times(2)).prepareProposalPayment(5L, USER_ID, 50_000L, 2, null);
    }

    @Test
    @DisplayName("일반 의뢰 결제도 이전 대기 주문이 카카오페이에서 이미 결제됐으면 대체하지 않고 거부한다")
    void rejectsJobReprepareWhenPreviousOrderIsPaidAtProvider() {
        Payment previous = Payment.pending(11L, 21L, USER_ID, "old-order", 100_000L, Instant.EPOCH);
        previous.recordKakaoTid(PREVIOUS_TID);
        when(userService.getActiveUser("KAKAO_123"))
                .thenReturn(User.builder().id(USER_ID).role(UserRole.OWNER).build());
        when(ownerService.getOwnerProfile(USER_ID)).thenReturn(Owner.builder().id(7L).build());
        when(jobService.getPayableJobForUpdate(11L, 7L))
                .thenReturn(Job.builder().id(11L).title("포스터 제작").budget(100_000L).build());
        when(jobService.getPayableApplication(11L, 21L))
                .thenReturn(JobApplication.builder().id(21L).jobId(11L).build());
        when(paymentService.findPendingPayment(11L)).thenReturn(Optional.of(previous));
        when(kakaoPayClient.order(PREVIOUS_TID)).thenReturn(providerOrder("SUCCESS_PAYMENT"));

        assertThatThrownBy(() -> service.createPending("KAKAO_123", 11L, PaymentPrepareRequest.of(21L, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_PAID));
        verify(paymentService, never()).preparePayment(any());
    }
}
