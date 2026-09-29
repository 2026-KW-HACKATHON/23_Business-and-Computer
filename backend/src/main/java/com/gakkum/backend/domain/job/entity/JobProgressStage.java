package com.gakkum.backend.domain.job.entity;

/** 화면에서 강조할 의뢰의 현재 진행 단계. 저장하지 않고 의뢰 상태와 최신 제출물에서 계산한다. */
public enum JobProgressStage {
    REQUESTED,  // 의뢰
    STARTED,  // 시작
    DRAFT,  // 초안
    REVISION,  // 수정
    COMPLETED  // 완료
}
