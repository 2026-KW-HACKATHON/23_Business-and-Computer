package com.gakkum.backend.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.gakkum.backend.global.exception.ErrorCode;

class ApiResponseTest {

    @Test
    void successWithDataCreatesSuccessResponse() {
        String data = "response data";

        ApiResponse<String> response = ApiResponse.success(data);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getError()).isNull();
    }

    @Test
    void successWithoutDataCreatesSuccessResponse() {
        ApiResponse<Void> response = ApiResponse.success();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    void failureCreatesErrorResponse() {
        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.INVALID_INPUT_VALUE);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError().code()).isEqualTo("COMMON_400");
        assertThat(response.getError().message()).isEqualTo("요청 값이 올바르지 않습니다.");
    }
}
