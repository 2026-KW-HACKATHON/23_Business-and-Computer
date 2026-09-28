package com.gakkum.backend.domain.review.entity;

/** 사장님이 리뷰에서 고를 수 있는 학생의 좋은 점. 코드 이름이 그대로 API와 DB에 저장된다. */
public enum ReviewPositivePoint {
    QUALITY_OUTPUT,       // 결과물이 좋아요
    ON_TIME_DELIVERY,     // 마감을 잘 지켜요
    FAST_COMMUNICATION,   // 소통이 빨라요
    KINDNESS,             // 친절해요
    REVISION_FEEDBACK     // 수정을 잘 반영해요
}
