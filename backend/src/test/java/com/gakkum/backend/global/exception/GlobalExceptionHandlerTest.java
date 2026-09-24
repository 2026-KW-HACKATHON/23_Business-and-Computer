package com.gakkum.backend.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.global.response.ApiResponse;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("비즈니스 예외는 data 필드 없이 정의된 오류 응답을 반환한다")
    void businessExceptionReturnsConfiguredErrorResponse() throws Exception {
        mockMvc.perform(get("/test/business-error"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))
            .andExpect(jsonPath("$.error.message").value("요청 값이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("잘못된 요청 값은 공통 입력 오류를 반환한다")
    void invalidRequestReturnsCommonValidationError() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))
            .andExpect(jsonPath("$.error.message").value("요청 값이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("잘못된 JSON 본문은 공통 입력 오류를 반환한다")
    void malformedRequestBodyReturnsCommonValidationError() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 내부 정보를 숨긴 서버 오류를 반환한다")
    void unexpectedExceptionReturnsSafeInternalServerError() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.error.code").value("COMMON_500"))
            .andExpect(jsonPath("$.error.message").value("서버 내부 오류가 발생했습니다."))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("sensitive error message")
            )));
    }

    @Test
    @DisplayName("데이터 충돌 예외는 409 오류를 반환한다")
    void dataIntegrityViolationReturnsConflict() throws Exception {
        mockMvc.perform(get("/test/data-conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_409"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business-error")
        ApiResponse<Void> businessError() {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        @PostMapping("/test/validation")
        ApiResponse<Void> validation(@Valid @RequestBody TestRequest request) {
            return ApiResponse.success();
        }

        @GetMapping("/test/unexpected-error")
        ApiResponse<Void> unexpectedError() {
            throw new IllegalStateException("sensitive error message");
        }

        @GetMapping("/test/data-conflict")
        ApiResponse<Void> dataConflict() {
            throw new DataIntegrityViolationException("duplicate");
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
