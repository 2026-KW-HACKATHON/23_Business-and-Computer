package com.gakkum.backend.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_400", "요청 값이 올바르지 않습니다."),
    SPECIALTY_NOT_FOUND(HttpStatus.BAD_REQUEST, "SPECIALTY_400", "존재하지 않는 특기가 포함되어 있습니다."),
    DUPLICATE_SPECIALTY(HttpStatus.BAD_REQUEST, "SPECIALTY_400_DUPLICATE", "중복된 특기가 포함되어 있습니다."),
    BUSINESS_CATEGORY_NOT_FOUND(HttpStatus.BAD_REQUEST, "CATEGORY_400", "존재하지 않는 업종입니다."),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_404", "존재하지 않는 의뢰입니다."),
    JOB_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_APPLICATION_404", "존재하지 않는 지원서입니다."),
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_ROOM_404", "존재하지 않는 채팅방입니다."),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_MESSAGE_404", "채팅방에서 메시지를 찾을 수 없습니다."),
    CHAT_FORBIDDEN(HttpStatus.FORBIDDEN, "CHAT_403", "채팅방에 접근할 수 없습니다."),
    CHAT_MESSAGE_CONFLICT(HttpStatus.CONFLICT, "CHAT_MESSAGE_409", "같은 메시지 ID로 다른 내용을 보낼 수 없습니다."),
    CHAT_UPLOAD_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "CHAT_UPLOAD_502", "첨부 파일 저장소에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    PAYMENT_NOT_AVAILABLE(HttpStatus.CONFLICT, "PAYMENT_409_UNAVAILABLE", "결제를 준비할 수 없는 의뢰 또는 지원서입니다."),
    PAYMENT_ALREADY_PAID(HttpStatus.CONFLICT, "PAYMENT_409_PAID", "이미 결제된 의뢰입니다."),
    PAYMENT_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "PAYMENT_403_OWNER", "사장님만 결제를 준비할 수 있습니다."),
    PAYMENT_READY_FAILED(HttpStatus.BAD_GATEWAY, "PAYMENT_502_READY", "결제창을 준비하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    PAYMENT_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_404_ORDER", "존재하지 않는 결제 주문입니다."),
    PAYMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "PAYMENT_403_FORBIDDEN", "본인의 결제 주문만 승인할 수 있습니다."),
    PAYMENT_APPROVAL_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "PAYMENT_502_APPROVAL", "결제 승인 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    PAYMENT_RESULT_MISMATCH(HttpStatus.BAD_GATEWAY, "PAYMENT_502_MISMATCH", "결제 승인 정보를 확인하지 못했습니다."),
    OWNER_PROFILE_NOT_FOUND(HttpStatus.FORBIDDEN, "OWNER_403", "사장님 프로필이 존재하지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER_409_EMAIL", "이미 사용 중인 이메일입니다."),
    STUDENT_EMAIL_VERIFICATION_INVALID(HttpStatus.BAD_REQUEST, "STUDENT_EMAIL_400", "이메일 인증번호가 올바르지 않거나 만료되었습니다."),
    STUDENT_EMAIL_VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN, "STUDENT_EMAIL_403", "학생 이메일 인증이 필요합니다."),
    STUDENT_EMAIL_VERIFICATION_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "STUDENT_EMAIL_429", "인증번호 재발송은 잠시 후 다시 시도해 주세요."),
    STUDENT_EMAIL_DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "STUDENT_EMAIL_503", "인증 메일을 발송하지 못했습니다."),
    OWNER_BUSINESS_VERIFICATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "OWNER_BUSINESS_503", "사업자등록정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    ALREADY_REGISTERED(HttpStatus.CONFLICT, "USER_409_REGISTERED", "이미 회원가입이 완료된 사용자입니다."),
    DUPLICATE_STUDENT_NUMBER(HttpStatus.CONFLICT, "STUDENT_409_NUMBER", "이미 사용 중인 학번입니다."),
    DUPLICATE_BUSINESS_NUMBER(HttpStatus.CONFLICT, "OWNER_409_BUSINESS_NUMBER", "이미 사용 중인 사업자등록번호입니다."),
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
