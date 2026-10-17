package com.gakkum.backend.global.ratelimit;

/**
 * 사용자별 요청 횟수를 제한하는 작업. 외부 서비스 호출(메일·국세청·카카오페이)이나 저장소 업로드 URL 발급처럼
 * 한 사용자가 반복해서 부르면 비용이 드는 요청과, 행을 새로 만들어 다른 사용자에게 보이거나 알림이 가는 쓰기만 둔다.
 * 작업마다 따로 센다.
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
    JOB_SUBMISSION_FILE_UPLOAD,
    /** 의뢰 등록. 탐색 목록에 바로 보인다 */
    JOB_CREATE,
    /** 제안 보내기. 탐색 목록에 보이고 받은 사장님에게 알림이 간다 */
    PROPOSAL_CREATE,
    /** 의뢰 지원. 사장님에게 알림이 간다 */
    JOB_APPLICATION_CREATE,
    /** 채팅 메시지 보내기. 글 · 첨부 메시지가 같은 횟수를 나눠 쓴다 */
    CHAT_MESSAGE_SEND,
    /** 결제 준비(카카오페이 결제창 준비 호출). 의뢰 결제와 제안 결제가 같은 횟수를 나눠 쓴다 */
    PAYMENT_PREPARE
}
