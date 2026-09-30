package com.gakkum.backend.domain.proposal.dto;

/** 탐색 목록에서 제안을 읽는 순서 */
public enum ProposalExploreOrder {
    LATEST,  // 생성 최신순
    OLDEST,  // 생성 오래된순
    LIKES  // 좋아요 많은순
}
