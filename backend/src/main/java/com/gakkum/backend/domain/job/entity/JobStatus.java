package com.gakkum.backend.domain.job.entity;

public enum JobStatus {
    OPEN,  // 모집 중
    AWAITING_START,  // 제안 결제 완료, 학생의 작업 시작 대기
    MATCHED,  // 학생과 매칭 완료
    CLOSED,  // 종료됨
    CANCELLED  // 사장님이 취소했거나 제안한 학생이 작업 시작 전에 의뢰서를 거절함
}
