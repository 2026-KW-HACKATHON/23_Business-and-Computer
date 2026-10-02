package com.gakkum.backend.application.explore.dto;

/**
 * 탐색 목록 정렬. 최신순·오래된순은 생성 시각 기준이고 같은 시각이면 제안을 의뢰보다 앞에 두고 같은 방향의 ID 순으로 고정한다.
 * 좋아요순은 제안에만 허용하며 좋아요 수 내림차순, 생성 시각 내림차순, ID 내림차순이다.
 */
public enum ExploreSort {
    LATEST,
    OLDEST,
    LIKES
}
