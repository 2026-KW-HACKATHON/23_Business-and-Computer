package com.gakkum.backend.common.response;

import com.gakkum.backend.common.exception.ErrorCode;

public record ErrorResponse(
    String code,
    String message
) {

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }
}
