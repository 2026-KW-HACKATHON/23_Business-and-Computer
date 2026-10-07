package com.gakkum.backend.domain.notification.entity;

public enum NotificationType {
    JOB_DRAFT_SUBMITTED,
    PROPOSAL_RECEIVED,
    JOB_APPLICATION_RECEIVED,
    CHAT_MESSAGE_RECEIVED,
    PAYMENT_COMPLETED,
    // 작업 완료. 지급 완료를 뜻하지 않는다
    JOB_COMPLETED,
    // 공감 10개 단위 도달. 당시 수치는 본문에 저장한다
    PROPOSAL_LIKE_MILESTONE_REACHED
}
