package com.gakkum.backend.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.gakkum.backend.common.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직에서 발생한 예외를 정의된 에러 코드와 HTTP 상태로 응답
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return createErrorResponse(exception.getErrorCode());
    }

    /**
     * 요청 데이터 검증 실패를 공통 입력값 오류 응답으로 변환
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception) {
        return createErrorResponse(ErrorCode.INVALID_INPUT_VALUE);
    }

    /**
     * 예상하지 못한 예외를 로깅하고 내부 정보를 노출하지 않는 서버 오류로 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return createErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> createErrorResponse(ErrorCode errorCode) {
        return ResponseEntity
            .status(errorCode.getStatus())
            .body(ApiResponse.failure(errorCode));
    }
}
