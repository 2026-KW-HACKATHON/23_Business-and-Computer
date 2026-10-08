package com.gakkum.backend.global.exception;

/**
 * 파일 전송을 끝까지 마치지 못했을 때 던진다.
 * 응답을 보내기 시작한 뒤라면 오류 본문을 덧붙일 수 없으므로, 처리기가 그대로 다시 던져 컨테이너가 연결을 끊게 한다.
 */
public class DownloadAbortedException extends BusinessException {

    public DownloadAbortedException(ErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}
