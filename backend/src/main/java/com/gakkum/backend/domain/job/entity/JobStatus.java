package com.gakkum.backend.domain.job.entity;

public enum JobStatus {
    OPEN,  // 모집 중
    MATCHED,  // 학생과 매칭 완료
    CLOSED,  // 종료됨
    CANCELLED  // 사장님이 취소함
}
