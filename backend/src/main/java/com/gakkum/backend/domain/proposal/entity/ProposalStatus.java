package com.gakkum.backend.domain.proposal.entity;

/** 제안의 진행 상태 */
public enum ProposalStatus {
    PENDING,  // 결제 전
    AWAITING_START,  // 사장님 결제 완료, 학생의 작업 시작 대기
    ACCEPTED,  // 학생이 작업을 시작함
    REJECTED,
    CANCELLED  // 결제 전에 제안한 학생이 취소함. 목록과 탐색에서 빠진다
}
