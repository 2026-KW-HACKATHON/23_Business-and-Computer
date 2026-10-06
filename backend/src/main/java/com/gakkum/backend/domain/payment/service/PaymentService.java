package com.gakkum.backend.domain.payment.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PreparePaymentCommand;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.RefundedPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryData;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final Clock clock;

    /**
     * 결제 대기(PENDING) 주문 생성. 결제 가능한 의뢰·지원인지는 호출하는 쪽이 먼저 검증하고 값만 넘긴다.
     * @param command 의뢰 ID, 지원 ID, 사장님 사용자 ID, 서버 기준 결제 금액(의뢰 예산)
     * @return 저장된 PENDING 주문
     */
    public Payment preparePayment(PreparePaymentCommand command) {

        // 예외: 결제 금액(의뢰 가격)이 없거나 0 이하인 경우
        if (command.getAmount() == null || command.getAmount() <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        // 예외: 이미 결제가 완료된 의뢰(Job)인 경우
        if (paymentRepository.existsByJobIdAndStatus(command.getJobId(), PaymentStatus.PAID)) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID);
        }

        // 이미 PENDING 상태의 결제 시도가 있다면(현재가 재시도) 이전 요청을 무효 처리
        paymentRepository.findByJobIdAndStatus(command.getJobId(), PaymentStatus.PENDING)
                .ifPresent(previous -> {
                    previous.supersede();
                    paymentRepository.flush();
                });

        Payment payment = Payment.pending(
                command.getJobId(),
                command.getJobApplicationId(),
                command.getOwnerUserId(),
                UUID.randomUUID().toString(),
                command.getAmount(),
                Instant.now(clock));

        return paymentRepository.save(payment);
    }

    /**
     * 제안 결제의 대기(PENDING) 주문 생성. 결제할 수 있는 제안인지는 호출하는 쪽이 제안 행을 잠가 먼저 검증하고 값만 넘긴다.
     * 사장님이 입력한 수정 횟수와 한마디는 주문에 보존했다가 승인 시 의뢰로 옮긴다.
     * @param proposalId
     * @param ownerUserId
     * @param amount 서버 기준 결제 금액(제안 작업비)
     * @param revisionCount
     * @param messageToStudent 입력하지 않았으면 null
     * @return 저장된 PENDING 주문
     */
    public Payment prepareProposalPayment(
            Long proposalId, String ownerUserId, Long amount, Integer revisionCount, String messageToStudent) {

        // 예외: 결제 금액(제안 작업비)이 없거나 0 이하인 경우
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        // 예외: 이미 결제가 완료된 제안인 경우
        if (paymentRepository.existsByProposalIdAndStatus(proposalId, PaymentStatus.PAID)) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID);
        }

        // 이미 PENDING 상태의 결제 시도가 있다면(현재가 재시도) 이전 요청을 무효 처리
        paymentRepository.findByProposalIdAndStatus(proposalId, PaymentStatus.PENDING)
                .ifPresent(previous -> {
                    previous.supersede();
                    paymentRepository.flush();
                });

        return paymentRepository.save(Payment.pendingForProposal(
                proposalId,
                ownerUserId,
                UUID.randomUUID().toString(),
                amount,
                revisionCount,
                messageToStudent,
                Instant.now(clock)));
    }

    /**
     * 제안의 결제 승인 시각. 결제된 제안에는 승인된 결제(PAID·REFUNDED)가 반드시 있어야 한다.
     * @param proposalId
     * @return 결제 승인 시각, 승인된 결제가 없으면 데이터 오류(500)
     */
    @Transactional(readOnly = true)
    public Instant getProposalPaidAt(Long proposalId) {
        return paymentRepository
                .findByProposalIdAndStatusIn(proposalId, List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED))
                .map(Payment::getApprovedAt)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    /** 의뢰의 결제 대기(PENDING) 주문. 재준비가 대체하기 전에 실제 결제 여부를 확인하는 데 쓴다. */
    @Transactional(readOnly = true)
    public Optional<Payment> findPendingPayment(Long jobId) {
        return paymentRepository.findByJobIdAndStatus(jobId, PaymentStatus.PENDING);
    }

    /** 제안의 결제 대기(PENDING) 주문. 재준비가 대체하기 전에 실제 결제 여부를 확인하는 데 쓴다. */
    @Transactional(readOnly = true)
    public Optional<Payment> findPendingProposalPayment(Long proposalId) {
        return paymentRepository.findByProposalIdAndStatus(proposalId, PaymentStatus.PENDING);
    }

    @Transactional
    public void recordKakaoTid(String orderId, String tid) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE));
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getKakaoTid() != null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        payment.recordKakaoTid(tid);
    }

    @Transactional
    public void failReady(String orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(Payment::failReady);
    }

    /**
     * 취소된 진행 중 의뢰의 결제 완료(PAID) 주문을 환불 처리한다. 결제 금액의 20%는 학생 보상금으로 남기고 나머지를 환불한다.
     * 해커톤 범위에서는 카카오페이 결제 취소 API를 호출하지 않고 환불 금액만 기록한다.
     * @param jobId
     * @return 결제 금액, 학생 보상금, 환불 금액, 환불 처리 시각
     */
    @Transactional
    public RefundedPaymentData refundOnCancel(Long jobId) {
        Payment payment = paymentRepository.findByJobIdAndStatus(jobId, PaymentStatus.PAID)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        payment.refundOnCancel(Instant.now(clock));
        return new RefundedPaymentData(payment.getAmount(), payment.getStudentCompensationAmount(),
                payment.getRefundAmount(), payment.getRefundedAt());
    }

    /**
     * 학생이 거절한 제안 의뢰의 결제 완료(PAID) 주문을 잠가 전액 환불 처리한다. 학생 보상금은 0원이다.
     * 해커톤 범위에서는 카카오페이 결제 취소 API를 호출하지 않고 환불 금액만 기록한다.
     * 제안과 의뢰 행을 잠근 뒤 호출한다. 결제 완료 주문이 없거나 주문이 가리키는 제안·결제한 사장님이 다르면 데이터 오류(500)다.
     * @param jobId
     * @param proposalId 의뢰를 만든 제안 ID
     * @param ownerUserId 의뢰한 사장님의 사용자 ID
     * @return 결제 금액, 학생 보상금(0원), 환불 금액(결제 금액 전액), 환불 처리 시각
     */
    @Transactional
    public RefundedPaymentData refundOnDecline(Long jobId, Long proposalId, String ownerUserId) {
        Payment payment = paymentRepository.findLockedByJobIdAndStatus(jobId, PaymentStatus.PAID)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        if (!proposalId.equals(payment.getProposalId()) || ownerUserId == null
                || !ownerUserId.equals(payment.getOwnerUserId()) || payment.getAmount() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        payment.refundOnDecline(Instant.now(clock));
        return new RefundedPaymentData(payment.getAmount(), payment.getStudentCompensationAmount(),
                payment.getRefundAmount(), payment.getRefundedAt());
    }

    /**
     * 결제 후 취소되거나 학생이 거절한 의뢰의 환불(REFUNDED) 주문 조회. 금액은 환불 시 저장한 값을 그대로 읽고 다시 계산하지 않는다.
     * @param jobId
     * @return 결제 금액, 학생 보상금, 환불 금액, 환불 처리 시각. 환불 주문이나 저장된 금액이 없으면 데이터 오류(500)
     */
    @Transactional(readOnly = true)
    public RefundedPaymentData getRefundedPayment(Long jobId) {
        Payment payment = paymentRepository.findByJobIdAndStatus(jobId, PaymentStatus.REFUNDED)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        if (payment.getRefundAmount() == null || payment.getStudentCompensationAmount() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return new RefundedPaymentData(payment.getAmount(), payment.getStudentCompensationAmount(),
                payment.getRefundAmount(), payment.getRefundedAt());
    }

    /**
     * 의뢰의 결제 완료(PAID) 주문 조회. 매칭 이후 의뢰에는 결제 완료 주문이 반드시 있어야 한다.
     * @param jobId
     * @return 주문 ID, 결제 금액, 결제 승인 시각
     */
    @Transactional(readOnly = true)
    public ApprovedPaymentData getPaidPayment(Long jobId) {
        Payment payment = paymentRepository.findByJobIdAndStatus(jobId, PaymentStatus.PAID)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        if (payment.getApprovedAt() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return new ApprovedPaymentData(payment.getOrderId(), payment.getAmount(), payment.getApprovedAt());
    }

    /**
     * 사장님 본인의 결제 내역 조회. 승인된 결제(PAID·REFUNDED)만 승인 시각 최신순으로 반환한다.
     * @param ownerUserId
     * @return 결제 내역, 없으면 빈 목록. 승인 시각이 없는 결제는 데이터 오류(500)
     */
    @Transactional(readOnly = true)
    public List<PaymentHistoryData> getPaymentHistory(String ownerUserId) {
        List<Payment> payments = paymentRepository.findByOwnerUserIdAndStatusInOrderByApprovedAtDescIdDesc(
                ownerUserId, List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED));
        if (payments.stream().anyMatch(payment -> payment.getApprovedAt() == null)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return payments.stream().map(PaymentHistoryData::from).toList();
    }

    /**
     * 학생이 담당하는 의뢰에 연결된 정산 내역 조회. 일반 결제와 제안 결제를 함께, 승인된 결제(PAID·REFUNDED)만 승인 시각 최신순으로 반환한다.
     * @param jobIds 학생 본인이 담당하는 의뢰 ID 목록
     * @return 정산 내역, 없으면 빈 목록. 승인 시각이 없는 결제는 데이터 오류(500)
     */
    @Transactional(readOnly = true)
    public List<SettlementHistoryData> getSettlementHistory(Collection<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        List<Payment> payments = paymentRepository.findByJobIdInAndStatusInOrderByApprovedAtDescIdDesc(
                jobIds, List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED));
        if (payments.stream().anyMatch(payment -> payment.getApprovedAt() == null)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return payments.stream().map(SettlementHistoryData::from).toList();
    }
}
