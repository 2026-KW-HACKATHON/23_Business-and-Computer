package com.gakkum.backend.global.ratelimit;

/**
 * 사용자별 요청 횟수를 제한하는 작업. 외부 서비스 호출(메일·국세청)이나 저장소 업로드 URL 발급처럼
 * 한 사용자가 반복해서 부르면 비용이 드는 요청만 둔다. 작업마다 따로 센다.
 */
public enum RateLimitedAction {
    /** 학생 이메일 인증번호 발송. 60초 재발송 대기와 별도로 센다 */
    STUDENT_EMAIL_SEND,
    /** 국세청 사업자등록정보 진위 확인. 진위 확인 API와 사장님 가입이 같은 횟수를 나눠 쓴다 */
    OWNER_BUSINESS_VERIFICATION,
    /** 프로필·매장·의뢰·제안 사진 업로드 URL 발급 */
    IMAGE_UPLOAD,
    /** 채팅 첨부 파일 업로드 URL 발급 */
    CHAT_ATTACHMENT_UPLOAD,
    /** 작업물 파일 업로드 URL 발급 */
    JOB_SUBMISSION_FILE_UPLOAD
}
