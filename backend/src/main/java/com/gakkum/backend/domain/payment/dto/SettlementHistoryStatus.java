package com.gakkum.backend.domain.payment.dto;

/** 학생 정산 내역 화면용 상태. 저장된 PaymentStatus와 의뢰 상태를 조합해 계산한다. */
public enum SettlementHistoryStatus {
    SCHEDULED,  // 정산 예정: 결제 완료, 의뢰 진행 중
    SETTLED,  // 정산 완료: 결제 완료, 의뢰 완료
    START_COMPENSATION  // 착수 보상: 의뢰 취소로 학생 보상금만 지급함
}
