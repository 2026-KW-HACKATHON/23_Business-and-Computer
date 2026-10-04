package com.gakkum.backend.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateProposalJobCommand;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.PaymentResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedOrderData;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.domain.payment.repository.PaymentRepository.OrderTargetProjection;
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
    private final ChatRoomService chatRoomService = mock(ChatRoomService.class);
    private final ProposalService proposalService = mock(ProposalService.class);
    private final JobService jobService = mock(JobService.class);
    private final PaymentApprovalService service =
            new PaymentApprovalService(userService, jobRepository, jobApplicationRepository,
                    paymentRepository, kakaoPayClient, chatRoomService, proposalService, jobService);
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
        OrderTargetProjection projection = mock(OrderTargetProjection.class);
        when(projection.getJobId()).thenReturn(11L);
        when(projection.getProposalId()).thenReturn(null);
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

        ApprovedOrderData result = service.approve("KAKAO_123", "order-123", "pg-123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getApprovedAt()).isEqualTo(APPROVED_AT);
        assertMatched();
        assertThat(result.amount()).isEqualTo(100_000L);
        verify(chatRoomService).createIfAbsent(11L);
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

        ApprovedOrderData result = service.approve("KAKAO_123", "order-123", "pg-123");

        assertThat(result.approvedAt()).isEqualTo(APPROVED_AT);
        assertMatched();
        verify(chatRoomService).createIfAbsent(11L);
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
        verify(chatRoomService).createIfAbsent(11L);
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

    // 제안 5번(학생 31, 사장님 프로필 7, 작업비 100,000원, 초안 3일·최종 7일)의 결제 주문
    private Payment proposalPending() {
        Payment payment = Payment.pendingForProposal(
                5L, OWNER_ID, "order-123", 100_000L, 2, "매장 분위기에 맞춰 주세요.", Instant.EPOCH);
        payment.recordKakaoTid(TID);
        return payment;
    }

    private Proposal arrangeProposal(Payment payment, ProposalStatus status) {
        Proposal proposal = Proposal.builder().id(5L).studentProfileId(31L).ownerProfileId(7L)
                .title("메뉴판 개선 제안").customerProblem("문제").proposedSolution("해결").workPlan("계획")
                .proposedFee(100_000L).draftDays(3).finalDays(7).status(status).build();
        when(userService.getActiveUser("KAKAO_123"))
                .thenReturn(User.builder().id(OWNER_ID).role(UserRole.OWNER).build());
        OrderTargetProjection projection = mock(OrderTargetProjection.class);
        when(projection.getProposalId()).thenReturn(5L);
        when(paymentRepository.findProjectedByOrderId("order-123")).thenReturn(Optional.of(projection));
        when(proposalService.getProposalForUpdate(5L)).thenReturn(proposal);
        when(proposalService.getSpecialtyIds(5L)).thenReturn(List.of(3L, 11L));
        when(jobService.findJobByProposalIdForUpdate(5L)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId("order-123")).thenReturn(Optional.of(payment));
        when(kakaoPayClient.cid()).thenReturn("TC0ONETIME");
        when(jobService.createAwaitingStartJob(any(CreateProposalJobCommand.class)))
                .thenAnswer(invocation -> {
                    CreateProposalJobCommand command = invocation.getArgument(0);
                    return Job.builder().id(42L).status(JobStatus.AWAITING_START)
                            .proposalId(command.getProposalId())
                            .selectedStudentProfileId(command.getStudentProfileId())
                            .draftDeadline(command.getDraftDeadline())
                            .finalDeadline(command.getFinalDeadline())
                            .build();
                });
        return proposal;
    }

    private CreateProposalJobCommand createdJobCommand() {
        ArgumentCaptor<CreateProposalJobCommand> captor = ArgumentCaptor.forClass(CreateProposalJobCommand.class);
        verify(jobService).createAwaitingStartJob(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("제안 결제를 승인하면 결제 기록·수락 대기 의뢰·제안 상태를 함께 저장하고 지원서와 채팅방은 만들지 않는다")
    void approvesProposalPayment() {
        Payment payment = proposalPending();
        Proposal proposal = arrangeProposal(payment, ProposalStatus.PENDING);
        when(kakaoPayClient.order(TID)).thenReturn(providerResult("READY"));
        when(kakaoPayClient.approve(TID, "order-123", OWNER_ID, "pg-123")).thenReturn(providerResult(null));

        ApprovedOrderData result = service.approve("KAKAO_123", "order-123", "pg-123");

        assertThat(result.jobId()).isEqualTo(42L);
        assertThat(result.jobStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(result.amount()).isEqualTo(100_000L);
        assertThat(result.approvedAt()).isEqualTo(APPROVED_AT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getJobId()).isEqualTo(42L);
        assertThat(payment.getJobApplicationId()).isNull();
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.AWAITING_START);

        CreateProposalJobCommand command = createdJobCommand();
        assertThat(command.getOwnerProfileId()).isEqualTo(7L);
        assertThat(command.getStudentProfileId()).isEqualTo(31L);
        assertThat(command.getProposalId()).isEqualTo(5L);
        assertThat(command.getTitle()).isEqualTo("메뉴판 개선 제안");
        assertThat(command.getDescription()).isEqualTo("[고객 문제]\n문제\n\n[해결 방안]\n해결\n\n[작업 계획]\n계획");
        assertThat(command.getBudget()).isEqualTo(100_000L);
        // 승인 시각 2026-09-26T03:00Z는 한국 날짜 9월 26일
        assertThat(command.getDraftDeadline()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(command.getFinalDeadline()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(command.getRevisionCount()).isEqualTo(2);
        assertThat(command.getAcceptanceMessage()).isEqualTo("매장 분위기에 맞춰 주세요.");
        assertThat(command.getSpecialtyIds()).containsExactly(3L, 11L);
        verifyNoInteractions(chatRoomService, jobApplicationRepository, jobRepository);
    }

    @Test
    @DisplayName("제안 결제 승인은 제안 → 의뢰 → 결제 순서로 잠근다")
    void locksProposalThenJobThenPayment() {
        arrangeProposal(proposalPending(), ProposalStatus.PENDING);
        when(kakaoPayClient.order(TID)).thenReturn(providerResult("SUCCESS_PAYMENT"));

        service.approve("KAKAO_123", "order-123", "pg-123");

        InOrder locks = inOrder(proposalService, jobService, paymentRepository);
        locks.verify(paymentRepository).findProjectedByOrderId("order-123");
        locks.verify(proposalService).getProposalForUpdate(5L);
        locks.verify(jobService).findJobByProposalIdForUpdate(5L);
        locks.verify(paymentRepository).findByOrderId("order-123");
    }

    @Test
    @DisplayName("제안 의뢰의 마감일은 UTC 날짜가 아니라 승인 시각의 한국 날짜에 제안 기간을 더해 확정한다")
    void fixesDeadlinesFromKoreanApprovalDate() {
        arrangeProposal(proposalPending(), ProposalStatus.PENDING);
        // UTC로는 9월 30일 15:30, 한국 시간으로는 10월 1일 00:30
        Instant approvedAt = Instant.parse("2026-09-30T15:30:00Z");
        when(kakaoPayClient.order(TID)).thenReturn(new PaymentResult(
                TID, "TC0ONETIME", "order-123", OWNER_ID, 100_000L, "SUCCESS_PAYMENT", approvedAt));

        service.approve("KAKAO_123", "order-123", "pg-123");

        CreateProposalJobCommand command = createdJobCommand();
        assertThat(command.getDraftDeadline()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(command.getFinalDeadline()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    @DisplayName("외부 승인은 성공했지만 저장하지 못한 제안 결제는 재요청에서 주문 조회 결과로 복구하고 승인을 다시 요청하지 않는다")
    void recoversProposalPaymentFromProviderOrder() {
        Payment payment = proposalPending();
        Proposal proposal = arrangeProposal(payment, ProposalStatus.PENDING);
        when(kakaoPayClient.order(TID)).thenReturn(providerResult("SUCCESS_PAYMENT"));

        ApprovedOrderData result = service.approve("KAKAO_123", "order-123", "pg-123");

        assertThat(result.jobStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.AWAITING_START);
        verify(kakaoPayClient, never()).approve(TID, "order-123", OWNER_ID, "pg-123");
    }

    @Test
    @DisplayName("이미 승인된 제안 결제의 재요청은 기존 의뢰를 반환하고 시작된 의뢰와 수락된 제안의 상태·마감일을 되돌리지 않는다")
    void repeatedProposalApprovalReturnsExistingJob() {
        Payment payment = proposalPending();
        payment.approve(APPROVED_AT);
        payment.linkJob(42L);
        Proposal proposal = arrangeProposal(payment, ProposalStatus.ACCEPTED);
        Job started = Job.builder().id(42L).status(JobStatus.MATCHED).proposalId(5L)
                .draftDeadline(LocalDate.of(2026, 9, 29)).finalDeadline(LocalDate.of(2026, 10, 3)).build();
        when(jobService.findJobByProposalIdForUpdate(5L)).thenReturn(Optional.of(started));

        ApprovedOrderData result = service.approve("KAKAO_123", "order-123", "pg-123");

        assertThat(result.jobId()).isEqualTo(42L);
        assertThat(result.jobStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(result.approvedAt()).isEqualTo(APPROVED_AT);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(started.getDraftDeadline()).isEqualTo(LocalDate.of(2026, 9, 29));
        verify(jobService, never()).createAwaitingStartJob(any());
        verify(kakaoPayClient, never()).order(TID);
        verifyNoInteractions(chatRoomService);
    }

    @Test
    @DisplayName("다른 주문으로 이미 의뢰가 만들어진 제안은 새 주문의 승인을 거부하고 의뢰를 다시 만들지 않는다")
    void rejectsProposalPaidByOtherOrder() {
        arrangeProposal(proposalPending(), ProposalStatus.AWAITING_START);
        when(jobService.findJobByProposalIdForUpdate(5L)).thenReturn(Optional.of(
                Job.builder().id(42L).status(JobStatus.AWAITING_START).proposalId(5L).build()));

        assertCode(ErrorCode.PAYMENT_ALREADY_PAID);
        verify(kakaoPayClient, never()).order(TID);
        verify(jobService, never()).createAwaitingStartJob(any());
    }

    @Test
    @DisplayName("결제 전 상태가 아닌 제안의 주문은 카카오페이를 호출하지 않고 거부한다")
    void rejectsProposalThatIsNotPending() {
        arrangeProposal(proposalPending(), ProposalStatus.REJECTED);

        assertCode(ErrorCode.PAYMENT_NOT_AVAILABLE);
        verify(kakaoPayClient, never()).order(TID);
        verify(jobService, never()).createAwaitingStartJob(any());
    }

    @Test
    @DisplayName("다른 사장님의 제안 결제 주문은 카카오페이에 보내지 않고 거부한다")
    void rejectsOtherOwnerOfProposalPayment() {
        arrangeProposal(proposalPending(), ProposalStatus.PENDING);
        when(userService.getActiveUser("KAKAO_123"))
                .thenReturn(User.builder().id("another-owner").role(UserRole.OWNER).build());

        assertCode(ErrorCode.PAYMENT_FORBIDDEN);
        verify(kakaoPayClient, never()).order(TID);
    }

    @Test
    @DisplayName("제안 결제의 승인 결과를 확인할 수 없으면 의뢰를 만들지 않고 제안과 주문을 그대로 둔다")
    void leavesProposalUntouchedWhenApprovalUnknown() {
        Payment payment = proposalPending();
        Proposal proposal = arrangeProposal(payment, ProposalStatus.PENDING);
        when(kakaoPayClient.order(TID)).thenReturn(providerResult("READY"), providerResult("READY"));
        when(kakaoPayClient.approve(TID, "order-123", OWNER_ID, "pg-123"))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE));

        assertCode(ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getJobId()).isNull();
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.PENDING);
        verify(jobService, never()).createAwaitingStartJob(any());
    }

    @Test
    @DisplayName("주문 복구는 카카오페이에서 이미 결제된 주문을 조회 결과로 기록하고 승인을 새로 요청하지 않는다")
    void recoversPaidOrderWithoutApprovalRequest() {
        Payment payment = proposalPending();
        Proposal proposal = arrangeProposal(payment, ProposalStatus.PENDING);
        when(kakaoPayClient.order(TID)).thenReturn(providerResult("SUCCESS_PAYMENT"));

        ApprovedOrderData result = service.recoverPaidOrder("KAKAO_123", "order-123");

        assertThat(result.jobStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.AWAITING_START);
        verify(kakaoPayClient, never()).approve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("주문 복구는 아직 결제되지 않은 주문에 승인을 요청하지 않고 아무것도 바꾸지 않는다")
    void doesNotApproveUnpaidOrderOnRecovery() {
        Payment payment = proposalPending();
        Proposal proposal = arrangeProposal(payment, ProposalStatus.PENDING);
        when(kakaoPayClient.order(TID)).thenReturn(providerResult("READY"));

        assertThatThrownBy(() -> service.recoverPaidOrder("KAKAO_123", "order-123"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_AVAILABLE));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.PENDING);
        verify(kakaoPayClient, never()).approve(any(), any(), any(), any());
        verify(jobService, never()).createAwaitingStartJob(any());
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
