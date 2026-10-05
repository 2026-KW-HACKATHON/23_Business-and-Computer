package com.gakkum.backend.application.jwt.dto;

/** 개발용 테스트 로그인으로 고를 수 있는 역할. 가입 대기(PENDING)는 받지 않는다. */
public enum DevLoginRole {
    OWNER,
    STUDENT
}
