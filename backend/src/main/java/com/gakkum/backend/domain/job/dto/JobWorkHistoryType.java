package com.gakkum.backend.domain.job.dto;

/** 결과물 조회의 작업 이력 단계. */
public enum JobWorkHistoryType {
    STARTED,
    DRAFT_SUBMITTED,
    REVISION_REQUESTED,
    REVISION_SUBMITTED,
    COMPLETED
}
