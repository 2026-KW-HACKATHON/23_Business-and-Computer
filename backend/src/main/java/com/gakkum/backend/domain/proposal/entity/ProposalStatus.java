package com.gakkum.backend.domain.proposal.entity;

/** 제안의 진행 상태 */
public enum ProposalStatus {
    PENDING,  // 결제 전
    AWAITING_START,  // 사장님 결제 완료, 학생의 작업 시작 대기
    ACCEPTED,  // 학생이 작업을 시작함
    REJECTED,  // 받은 사장님이 결제 전에 거절했거나, 결제 후 제안한 학생이 작업 시작 전에 의뢰서를 거절함. 탐색에서 빠진다
    CANCELLED  // 결제 전에 제안한 학생이 취소함. 목록과 탐색에서 빠진다
}
