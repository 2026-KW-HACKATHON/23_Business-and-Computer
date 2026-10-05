package com.gakkum.backend.application.payment.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.payment.dto.PaymentPrepareRequest;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PreparePaymentCommand;
import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PrepareProposalPaymentCommand;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PendingPaymentData;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentPreparationService {

    private final UserService userService;
    private final OwnerService ownerService;
    private final JobService jobService;
    private final PaymentService paymentService;
    private final ProposalService proposalService;
    private final KakaoPayClient kakaoPayClient;

    @Transactional
    public PendingPaymentData createPending(String username, Long jobId, PaymentPrepareRequest request) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.PAYMENT_OWNER_REQUIRED);
        }
        Owner owner = ownerService.getOwnerProfile(user.getId());
        Job job = jobService.getPayableJobForUpdate(jobId, owner.getId());
        JobApplication application = jobService.getPayableApplication(jobId, request.getJobApplicationId());
        rejectIfPaidAtProvider(paymentService.findPendingPayment(job.getId()));
        Payment payment = paymentService.preparePayment(PreparePaymentCommand.of(
                job.getId(), application.getId(), user.getId(), job.getBudget()));
        return new PendingPaymentData(payment.getOrderId(), payment.getAmount(), job.getTitle(), user.getId());
    }

    /**
     * 사장님이 받은 제안의 결제 대기 주문을 만든다. 제안 행을 잠가 같은 제안의 결제 준비·승인과 순서대로 처리한다.
     * 결제 금액은 제안 작업비, 주문 이름은 제안 타이틀로 서버가 정한다.
     */
    @Transactional
    public PendingPaymentData createPendingForProposal(PrepareProposalPaymentCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.PAYMENT_OWNER_REQUIRED);
        }
        Owner owner = ownerService.getOwnerProfile(user.getId());
        Proposal proposal = proposalService.getPayableProposalForUpdate(command.getProposalId(), owner.getId());
        rejectIfPaidAtProvider(paymentService.findPendingProposalPayment(proposal.getId()));
        Payment payment = paymentService.prepareProposalPayment(
                proposal.getId(), user.getId(), proposal.getProposedFee(),
                command.getRevisionCount(), command.getMessageToStudent());
        return new PendingPaymentData(payment.getOrderId(), payment.getAmount(), proposal.getTitle(), user.getId());
    }

    /**
     * 이전 대기 주문을 새 주문으로 대체하기 전에 카카오페이에서 이미 결제된 주문인지 확인한다.
     * 외부 승인은 성공했지만 서버 저장이 실패한 주문은 PENDING으로 남는데, 이를 대체하면 승인 재요청으로 복구할 수 없게 된다.
     * 결제된 주문이면 대체하지 않고 거부하며, 결제 여부를 조회하지 못하면 조회 오류를 그대로 올려 대체하지 않는다.
     * 거래번호가 없는 주문은 결제창이 열린 적이 없어 결제됐을 수 없다.
     */
    private void rejectIfPaidAtProvider(Optional<Payment> previous) {
        previous.map(Payment::getKakaoTid).ifPresent(tid -> {
            if ("SUCCESS_PAYMENT".equals(kakaoPayClient.order(tid).status())) {
                throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID);
            }
        });
    }
}
