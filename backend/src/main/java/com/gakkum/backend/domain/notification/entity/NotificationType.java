package com.gakkum.backend.domain.notification.entity;

public enum NotificationType {
    JOB_DRAFT_SUBMITTED,
    PROPOSAL_RECEIVED,
    JOB_APPLICATION_RECEIVED,
    // 저장된 알림과의 호환을 위해 남긴다. 발행하지 않는다
    CHAT_MESSAGE_RECEIVED,
    // 저장된 알림과의 호환을 위해 남긴다. 발행하지 않는다
    PAYMENT_COMPLETED,
    // 작업 완료. 지급 완료를 뜻하지 않는다. 저장된 알림과의 호환을 위해 남기고 발행하지 않는다
    JOB_COMPLETED,
    // 공감 10·30·50명 도달. 당시 수치는 본문에 저장한다
    PROPOSAL_LIKE_MILESTONE_REACHED,
    JOB_APPLICATION_SELECTED,
    JOB_APPLICATION_REJECTED,
    PROPOSAL_REJECTED,
    PROPOSAL_CANCELLED,
    // 사장님의 제안 결제 승인. 학생의 작업 시작 동의는 JOB_STARTED다
    PROPOSAL_ACCEPTED,
    JOB_STARTED,
    JOB_REVISION_REQUESTED,
    JOB_REVISION_SUBMITTED,
    JOB_REVIEW_REQUESTED,
    JOB_REVIEW_RECEIVED,
    JOB_RECRUITMENT_CANCELLED,
    JOB_CANCELLED_BY_OWNER,
    // 환불 기록 반영. 실제 자금 이동 완료를 뜻하지 않는다
    PAYMENT_REFUNDED,
    // 정산 기록 반영. 실제 자금 이동 완료를 뜻하지 않는다
    PAYMENT_SETTLED
}
