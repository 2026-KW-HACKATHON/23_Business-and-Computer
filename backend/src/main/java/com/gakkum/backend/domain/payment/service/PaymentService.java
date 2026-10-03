package com.gakkum.backend.domain.payment.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PreparePaymentCommand;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.RefundedPaymentData;
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
}
