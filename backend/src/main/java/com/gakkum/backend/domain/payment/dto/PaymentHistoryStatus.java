package com.gakkum.backend.domain.payment.dto;

/** 결제 내역 화면용 상태. 저장된 PaymentStatus와 의뢰 상태를 조합해 계산한다. */
public enum PaymentHistoryStatus {
    HELD,  // 보관중: 결제 완료, 의뢰 진행 중
    SETTLED,  // 정산 완료: 결제 완료, 의뢰 완료
    PARTIALLY_REFUNDED  // 부분 환불: 의뢰 취소로 학생 보상금을 뺀 금액을 환불함
}
