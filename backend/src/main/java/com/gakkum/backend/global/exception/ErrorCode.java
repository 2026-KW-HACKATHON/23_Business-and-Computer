package com.gakkum.backend.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_400", "요청 값이 올바르지 않습니다."),
    SPECIALTY_NOT_FOUND(HttpStatus.BAD_REQUEST, "SPECIALTY_400", "존재하지 않는 특기가 포함되어 있습니다."),
    DUPLICATE_SPECIALTY(HttpStatus.BAD_REQUEST, "SPECIALTY_400_DUPLICATE", "중복된 특기가 포함되어 있습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER_409_EMAIL", "이미 사용 중인 이메일입니다."),
    ALREADY_REGISTERED(HttpStatus.CONFLICT, "USER_409_REGISTERED", "이미 회원가입이 완료된 사용자입니다."),
    DUPLICATE_STUDENT_NUMBER(HttpStatus.CONFLICT, "STUDENT_409_NUMBER", "이미 사용 중인 학번입니다."),
    DATA_CONFLICT(HttpStatus.CONFLICT, "COMMON_409", "이미 존재하는 데이터와 충돌합니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
