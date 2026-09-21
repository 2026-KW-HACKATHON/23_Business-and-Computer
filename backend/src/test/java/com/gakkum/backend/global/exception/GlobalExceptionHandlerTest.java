package com.gakkum.backend.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    void businessExceptionReturnsConfiguredErrorResponse() throws Exception {
        mockMvc.perform(get("/test/business-error"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))
            .andExpect(jsonPath("$.error.message").value("요청 값이 올바르지 않습니다."));
    }

    @Test
    void invalidRequestReturnsCommonValidationError() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))
            .andExpect(jsonPath("$.error.message").value("요청 값이 올바르지 않습니다."));
    }

    @Test
    void unexpectedExceptionReturnsSafeInternalServerError() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.error.code").value("COMMON_500"))
            .andExpect(jsonPath("$.error.message").value("서버 내부 오류가 발생했습니다."))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("sensitive error message")
            )));
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
    }

    record TestRequest(@NotBlank String name) {
    }
}
