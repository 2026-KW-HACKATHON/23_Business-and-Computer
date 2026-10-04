package com.gakkum.backend.application.payment.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateProposalJobCommand;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.PaymentResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedOrderData;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.domain.payment.repository.PaymentRepository.OrderTargetProjection;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentApprovalService {

    // 제안 의뢰의 마감일을 확정하는 기준 시간대
    private static final ZoneId DEADLINE_ZONE = ZoneId.of("Asia/Seoul");

    private final UserService userService;
    private final JobRepository jobRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final PaymentRepository paymentRepository;
    private final KakaoPayClient kakaoPayClient;
    private final ChatRoomService chatRoomService;
    private final ProposalService proposalService;
    private final JobService jobService;

    @Transactional
    public ApprovedOrderData approve(String username, String orderId, String pgToken) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.PAYMENT_FORBIDDEN);
        }

        OrderTargetProjection target = paymentRepository.findProjectedByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
        if (target.getProposalId() != null) {
            return approveProposalPayment(user, orderId, pgToken, target.getProposalId());
        }

        Long jobId = target.getJobId();
        Job job = jobRepository.findLockedById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
        if (!payment.getOwnerUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.PAYMENT_FORBIDDEN);
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            match(job, payment);
            chatRoomService.createIfAbsent(jobId);
            return result(payment, job);
        }
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getKakaoTid() == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        if (paymentRepository.existsByJobIdAndStatus(jobId, PaymentStatus.PAID)) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID);
        }
        JobApplication application = getApplication(payment);
        if (job.getStatus() != JobStatus.OPEN || job.getSelectedStudentProfileId() != null
                || application.getStatus() != JobApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }

        return recordApproval(job, application, payment, confirmWithProvider(payment, user.getId(), pgToken));
    }

    /**
     * 카카오페이에서는 이미 결제됐지만 서버에 PENDING으로 남은 주문을 조회 결과로 복구한다. 승인을 새로 요청하지 않는다.
     * 결제 재준비가 이전 주문이 결제된 것을 확인했을 때 호출하며, 결제되지 않은 주문이면 아무것도 바꾸지 않고 거부한다.
     */
    @Transactional
    public ApprovedOrderData recoverPaidOrder(String username, String orderId) {
        return approve(username, orderId, null);
    }

    /**
     * 제안 결제 승인. 결제 기록·수락 대기 의뢰 생성·분류 복사·제안의 수락 대기 전환을 한 트랜잭션으로 저장한다.
     * 지원서와 채팅방은 만들지 않으며 작업은 학생이 시작할 때 진행 중이 된다.
     * 잠금 순서는 제안 → 의뢰 → 결제로 작업 시작과 같다.
     * 이미 승인된 주문의 재요청은 기존 의뢰를 그대로 반환하고 상태와 마감일을 되돌리지 않는다.
     */
    private ApprovedOrderData approveProposalPayment(User user, String orderId, String pgToken, Long proposalId) {
        Proposal proposal = proposalService.getProposalForUpdate(proposalId);
        Optional<Job> existingJob = jobService.findJobByProposalIdForUpdate(proposalId);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
        if (!payment.getOwnerUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.PAYMENT_FORBIDDEN);
        }
        // 잠금 전에 읽은 제안이 잠근 주문의 제안과 같은지 다시 확인한다
        if (!proposalId.equals(payment.getProposalId())) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            Job job = existingJob
                    .filter(found -> found.getId().equals(payment.getJobId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
            return result(payment, job);
        }
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getKakaoTid() == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        if (existingJob.isPresent() || paymentRepository.existsByProposalIdAndStatus(proposalId, PaymentStatus.PAID)) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID);
        }
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }

        PaymentResult confirmed = confirmWithProvider(payment, user.getId(), pgToken);
        if (confirmed.approvedAt() == null) {
            throw new BusinessException(ErrorCode.PAYMENT_RESULT_MISMATCH);
        }

        // 마감일은 승인 시각의 한국 날짜에 제안 기간을 더해 확정하고 이후 바꾸지 않는다
        LocalDate approvedDate = confirmed.approvedAt().atZone(DEADLINE_ZONE).toLocalDate();
        Job job = jobService.createAwaitingStartJob(CreateProposalJobCommand.of(
                proposal, proposalService.getSpecialtyIds(proposal.getId()), approvedDate,
                payment.getRevisionCount(), payment.getMessageToStudent()));
        proposal.awaitStart();
        payment.approve(confirmed.approvedAt());
        payment.linkJob(job.getId());
        return result(payment, job);
    }

    /**
     * 카카오페이에서 승인된 결제 결과를 가져온다. 이미 승인된 주문은 조회 결과를 그대로 쓰고, 승인 대기 중이면 승인을 요청한다.
     * 승인 요청이 실패해도 조회 결과가 승인 완료면 그 결과로 복구한다.
     */
    private PaymentResult confirmWithProvider(Payment payment, String ownerUserId, String pgToken) {
        PaymentResult current = kakaoPayClient.order(payment.getKakaoTid());
        validate(current, payment);
        if ("SUCCESS_PAYMENT".equals(current.status())) {
            return current;
        }
        // 승인 토큰이 없는 복구 요청은 이미 결제된 주문만 기록하고 승인을 새로 요청하지 않는다
        if (!isAwaitingApproval(current.status()) || pgToken == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }

        PaymentResult approved;
        try {
            approved = kakaoPayClient.approve(payment.getKakaoTid(), payment.getOrderId(), ownerUserId, pgToken);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE) {
                throw exception;
            }
            PaymentResult afterFailure = kakaoPayClient.order(payment.getKakaoTid());
            validate(afterFailure, payment);
            if ("SUCCESS_PAYMENT".equals(afterFailure.status())) {
                return afterFailure;
            }
            throw exception;
        }
        validate(approved, payment);
        return approved;
    }

    private void validate(PaymentResult result, Payment payment) {
        if (!payment.getKakaoTid().equals(result.tid())
                || !kakaoPayClient.cid().equals(result.cid())
                || !payment.getOrderId().equals(result.orderId())
                || !payment.getOwnerUserId().equals(result.ownerUserId())
                || !payment.getAmount().equals(result.amount())) {
            throw new BusinessException(ErrorCode.PAYMENT_RESULT_MISMATCH);
        }
    }

    private ApprovedOrderData recordApproval(
            Job job, JobApplication application, Payment payment, PaymentResult result) {
        if (result.approvedAt() == null) {
            throw new BusinessException(ErrorCode.PAYMENT_RESULT_MISMATCH);
        }
        job.match(application.getStudentProfileId());
        application.accept();
        payment.approve(result.approvedAt());
        chatRoomService.createIfAbsent(job.getId());
        return result(payment, job);
    }

    private void match(Job job, Payment payment) {
        JobApplication application = getApplication(payment);
        job.match(application.getStudentProfileId());
        application.accept();
    }

    private JobApplication getApplication(Payment payment) {
        JobApplication application = jobApplicationRepository.findById(payment.getJobApplicationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_APPLICATION_NOT_FOUND));
        if (!application.getJobId().equals(payment.getJobId())) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        return application;
    }

    private ApprovedOrderData result(Payment payment, Job job) {
        return new ApprovedOrderData(payment.getOrderId(), payment.getAmount(), payment.getApprovedAt(),
                job.getId(), job.getStatus());
    }

    private boolean isAwaitingApproval(String status) {
        return "READY".equals(status) || "SEND_TMS".equals(status)
                || "OPEN_PAYMENT".equals(status) || "SELECT_METHOD".equals(status)
                || "ARS_WAITING".equals(status) || "AUTH_PASSWORD".equals(status);
    }
}
