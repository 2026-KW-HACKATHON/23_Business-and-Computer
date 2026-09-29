package com.gakkum.backend.domain.payment.entity;

public enum PaymentStatus {
    PENDING,
    READY_FAILED,
    SUPERSEDED,
    PAID,
    REFUNDED  // 의뢰 취소로 학생 보상금을 뺀 금액을 환불 처리함
}
