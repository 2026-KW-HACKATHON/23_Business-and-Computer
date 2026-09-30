package com.gakkum.backend.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_400", "요청 값이 올바르지 않습니다."),
    SPECIALTY_NOT_FOUND(HttpStatus.BAD_REQUEST, "SPECIALTY_400", "존재하지 않는 특기가 포함되어 있습니다."),
    DUPLICATE_SPECIALTY(HttpStatus.BAD_REQUEST, "SPECIALTY_400_DUPLICATE", "중복된 특기가 포함되어 있습니다."),
    BUSINESS_CATEGORY_NOT_FOUND(HttpStatus.BAD_REQUEST, "CATEGORY_400", "존재하지 않는 업종입니다."),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_404", "존재하지 않는 의뢰입니다."),
    JOB_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_RESULT_404", "조회할 수 있는 결과물이 없습니다."),
    JOB_SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_SUBMISSION_404", "검토 대기 중인 제출물이 없습니다."),
    JOB_SUBMISSION_FORBIDDEN(HttpStatus.FORBIDDEN, "JOB_SUBMISSION_403", "의뢰에 매칭된 학생만 작업물을 제출할 수 있습니다."),
    JOB_SUBMISSION_NOT_AVAILABLE(HttpStatus.CONFLICT, "JOB_SUBMISSION_409_STATUS", "작업물을 제출할 수 없는 의뢰 상태입니다."),
    JOB_SUBMISSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "JOB_SUBMISSION_409_DUPLICATE", "이미 초안을 제출했습니다."),
    JOB_SUBMISSION_REVISION_NOT_REQUESTED(HttpStatus.CONFLICT, "JOB_SUBMISSION_409_REVISION_NOT_REQUESTED", "수정 요청을 받은 제출물이 없어 수정안을 제출할 수 없습니다."),
    JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "JOB_SUBMISSION_409_REVISION_LIMIT", "의뢰의 수정 가능 횟수를 모두 사용했습니다."),
    JOB_SUBMISSION_REVIEW_NOT_AVAILABLE(HttpStatus.CONFLICT, "JOB_SUBMISSION_409_REVIEW_STATUS", "제출물을 검토할 수 없는 의뢰 상태입니다."),
    JOB_SUBMISSION_ALREADY_REVIEWED(HttpStatus.CONFLICT, "JOB_SUBMISSION_409_REVIEWED", "이미 검토가 끝난 제출물입니다."),
    JOB_SUBMISSION_FILE_URL_INVALID(HttpStatus.BAD_REQUEST, "JOB_SUBMISSION_400_FILE_URL", "이 의뢰용으로 발급된 파일 URL이 아닙니다."),
    JOB_SUBMISSION_FILE_NOT_UPLOADED(HttpStatus.CONFLICT, "JOB_SUBMISSION_409_FILE_NOT_UPLOADED", "업로드가 끝나지 않은 파일이 있습니다."),
    JOB_SUBMISSION_FILE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "JOB_SUBMISSION_502", "작업 파일 저장소에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    JOB_CANCEL_NOT_AVAILABLE(HttpStatus.CONFLICT, "JOB_409_CANCEL", "취소할 수 없는 의뢰 상태입니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_404", "조회할 수 있는 리뷰가 없습니다."),
    REVIEW_STUDENT_REQUIRED(HttpStatus.FORBIDDEN, "REVIEW_403_STUDENT", "학생만 받은 리뷰를 조회할 수 있습니다."),
    REVIEW_NOT_AVAILABLE(HttpStatus.CONFLICT, "REVIEW_409_STATUS", "완료된 의뢰에만 리뷰를 작성할 수 있습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "REVIEW_409_DUPLICATE", "이미 리뷰를 작성한 의뢰입니다."),
    JOB_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_APPLICATION_404", "존재하지 않는 지원서입니다."),
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_ROOM_404", "존재하지 않는 채팅방입니다."),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_MESSAGE_404", "채팅방에서 메시지를 찾을 수 없습니다."),
    CHAT_FORBIDDEN(HttpStatus.FORBIDDEN, "CHAT_403", "채팅방에 접근할 수 없습니다."),
    CHAT_MESSAGE_CONFLICT(HttpStatus.CONFLICT, "CHAT_MESSAGE_409", "같은 메시지 ID로 다른 내용을 보낼 수 없습니다."),
    CHAT_UPLOAD_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "CHAT_UPLOAD_400_TYPE", "허용되지 않는 첨부 파일 형식입니다."),
    CHAT_UPLOAD_TOO_LARGE(HttpStatus.BAD_REQUEST, "CHAT_UPLOAD_400_SIZE", "첨부 파일 크기가 허용 범위를 넘었습니다."),
    CHAT_UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_UPLOAD_404", "첨부 업로드를 찾을 수 없습니다."),
    CHAT_UPLOAD_NOT_READY(HttpStatus.CONFLICT, "CHAT_UPLOAD_409_NOT_READY", "첨부 파일 업로드가 끝나지 않았거나 만료되었습니다."),
    CHAT_UPLOAD_ALREADY_USED(HttpStatus.CONFLICT, "CHAT_UPLOAD_409_USED", "이미 다른 메시지에 사용된 첨부 파일입니다."),
    CHAT_UPLOAD_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "CHAT_UPLOAD_502", "첨부 파일 저장소에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    MEDIA_UPLOAD_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "MEDIA_UPLOAD_400_TYPE", "허용되지 않는 사진 형식입니다."),
    MEDIA_UPLOAD_TOO_LARGE(HttpStatus.BAD_REQUEST, "MEDIA_UPLOAD_400_SIZE", "사진 크기가 허용 범위를 넘었습니다."),
    MEDIA_UPLOAD_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "MEDIA_UPLOAD_502", "사진 저장소에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    PAYMENT_NOT_AVAILABLE(HttpStatus.CONFLICT, "PAYMENT_409_UNAVAILABLE", "결제를 준비할 수 없는 의뢰 또는 지원서입니다."),
    PAYMENT_ALREADY_PAID(HttpStatus.CONFLICT, "PAYMENT_409_PAID", "이미 결제된 의뢰입니다."),
    PAYMENT_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "PAYMENT_403_OWNER", "사장님만 결제를 준비할 수 있습니다."),
    PAYMENT_READY_FAILED(HttpStatus.BAD_GATEWAY, "PAYMENT_502_READY", "결제창을 준비하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    PAYMENT_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_404_ORDER", "존재하지 않는 결제 주문입니다."),
    PAYMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "PAYMENT_403_FORBIDDEN", "본인의 결제 주문만 승인할 수 있습니다."),
    PAYMENT_APPROVAL_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "PAYMENT_502_APPROVAL", "결제 승인 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    PAYMENT_RESULT_MISMATCH(HttpStatus.BAD_GATEWAY, "PAYMENT_502_MISMATCH", "결제 승인 정보를 확인하지 못했습니다."),
    PROPOSAL_STUDENT_REQUIRED(HttpStatus.FORBIDDEN, "PROPOSAL_403_STUDENT", "학생만 제안을 보낼 수 있습니다."),
    PROPOSAL_IMAGE_URL_INVALID(HttpStatus.BAD_REQUEST, "PROPOSAL_400_IMAGE_URL", "제안용으로 발급된 사진 URL이 아닙니다."),
    PROPOSAL_IMAGE_NOT_UPLOADED(HttpStatus.CONFLICT, "PROPOSAL_409_IMAGE_NOT_UPLOADED", "업로드가 끝나지 않은 사진이 있습니다."),
    OWNER_NOT_FOUND(HttpStatus.NOT_FOUND, "OWNER_404", "존재하지 않는 사장님입니다."),
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
