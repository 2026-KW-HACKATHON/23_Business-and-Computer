package com.gakkum.backend.global.response;

import com.gakkum.backend.global.exception.ErrorCode;

public record ErrorResponse(
    String code,
    String message
) {

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }
}
