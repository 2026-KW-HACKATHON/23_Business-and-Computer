package com.gakkum.backend.application.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentApprovalService {

    private final UserService userService;
    private final JobRepository jobRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final PaymentRepository paymentRepository;
    private final KakaoPayClient kakaoPayClient;
    private final ChatRoomService chatRoomService;

    @Transactional
    public ApprovedPaymentData approve(String username, String orderId, String pgToken) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.PAYMENT_FORBIDDEN);
        }

        Long jobId = paymentRepository.findProjectedByOrderId(orderId)
                .map(JobIdProjection::getJobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
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
            return result(payment);
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

        PaymentResult current = kakaoPayClient.order(payment.getKakaoTid());
        validate(current, payment);
        if ("SUCCESS_PAYMENT".equals(current.status())) {
            return recordApproval(job, application, payment, current);
        }
        if (!isAwaitingApproval(current.status())) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }

        PaymentResult approved;
        try {
            approved = kakaoPayClient.approve(payment.getKakaoTid(), orderId, user.getId(), pgToken);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE) {
                throw exception;
            }
            PaymentResult afterFailure = kakaoPayClient.order(payment.getKakaoTid());
            validate(afterFailure, payment);
            if ("SUCCESS_PAYMENT".equals(afterFailure.status())) {
                return recordApproval(job, application, payment, afterFailure);
            }
            throw exception;
        }
        validate(approved, payment);
        return recordApproval(job, application, payment, approved);
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

    private ApprovedPaymentData recordApproval(
            Job job, JobApplication application, Payment payment, PaymentResult result) {
        if (result.approvedAt() == null) {
            throw new BusinessException(ErrorCode.PAYMENT_RESULT_MISMATCH);
        }
        job.match(application.getStudentProfileId());
        application.accept();
        payment.approve(result.approvedAt());
        chatRoomService.createIfAbsent(job.getId());
        return result(payment);
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

    private ApprovedPaymentData result(Payment payment) {
        return new ApprovedPaymentData(payment.getOrderId(), payment.getAmount(), payment.getApprovedAt());
    }

    private boolean isAwaitingApproval(String status) {
        return "READY".equals(status) || "SEND_TMS".equals(status)
                || "OPEN_PAYMENT".equals(status) || "SELECT_METHOD".equals(status)
                || "ARS_WAITING".equals(status) || "AUTH_PASSWORD".equals(status);
    }
}
