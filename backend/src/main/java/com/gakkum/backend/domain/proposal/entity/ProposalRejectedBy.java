package com.gakkum.backend.domain.proposal.entity;

/** 제안을 거절한 주체 */
public enum ProposalRejectedBy {
    OWNER,  // 제안을 받은 사장님이 결제 전에 거절함
    STUDENT  // 제안한 학생이 결제된 의뢰서를 작업 시작 전에 거절함
}
