package com.gakkum.backend.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.global.exception.ErrorCode;

import tools.jackson.databind.ObjectMapper;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("데이터가 있는 성공 응답은 null인 error 필드를 생략한다")
    void successWithDataCreatesSuccessResponse() {
        String data = "response data";

        ApiResponse<String> response = ApiResponse.success(data);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getError()).isNull();
        assertThat(objectMapper.writeValueAsString(response))
                .contains("\"success\":true", "\"data\":\"response data\"")
                .doesNotContain("\"error\"");
    }

    @Test
    @DisplayName("데이터가 없는 성공 응답은 success 필드만 포함한다")
    void successWithoutDataCreatesSuccessResponse() {
        ApiResponse<Void> response = ApiResponse.success();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNull();
        assertThat(objectMapper.writeValueAsString(response)).isEqualTo("{\"success\":true}");
    }

    @Test
    @DisplayName("실패 응답은 null인 data 필드를 생략하고 오류 정보를 포함한다")
    void failureCreatesErrorResponse() {
        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.INVALID_INPUT_VALUE);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError().code()).isEqualTo("COMMON_400");
        assertThat(response.getError().message()).isEqualTo("요청 값이 올바르지 않습니다.");
        assertThat(objectMapper.writeValueAsString(response))
                .contains("\"success\":false", "\"error\":{", "\"code\":\"COMMON_400\"")
                .doesNotContain("\"data\"");
    }
}
