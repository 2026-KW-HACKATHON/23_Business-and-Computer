package com.gakkum.backend.domain.job.dto;

import java.util.Arrays;
import java.util.Optional;

/** 의뢰 지원자 목록의 정렬 기준. 동률이면 모두 최신 지원순으로 정렬한다. */
public enum JobApplicationSort {
    LATEST,  // 지원 최신순
    RATING,  // 평균 별점 높은순
    COMPLETED;  // 완료 의뢰 많은순

    /** 대소문자와 공백까지 정확히 일치하는 값만 찾는다. */
    public static Optional<JobApplicationSort> find(String value) {
        return Arrays.stream(values())
                .filter(sort -> sort.name().equals(value))
                .findFirst();
    }
}
