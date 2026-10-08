package com.gakkum.backend.application.home.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.gakkum.backend.global.response.KoreaTime;

/** 홈 본문 응답(GET /me/home). 로그인 사용자의 역할에 따라 사장님 또는 학생 응답이 된다. */
public interface HomeResponse {

    String KIND_REQUEST = "request";
    String KIND_PROPOSAL = "proposal";
    String STAGE_DRAFT = "draft";
    String STAGE_FINAL = "final";

    /** 제안에서 시작한 작업이면 proposal, 사장님이 올린 의뢰면 request */
    static String kindOf(Long proposalId) {
        return proposalId == null ? KIND_REQUEST : KIND_PROPOSAL;
    }

    /** UTC로 저장된 시각의 한국 날짜. null은 그대로 둔다. */
    static LocalDate koreaDate(LocalDateTime utc) {
        return utc == null ? null : KoreaTime.from(utc).toLocalDate();
    }
}
