package com.gakkum.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@ExtendWith(OutputCaptureExtension.class)
class ControllerLoggingInterceptorTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new TestExceptionHandler())
            .addInterceptors(new ControllerLoggingInterceptor())
            .build();

    @Test
    @DisplayName("정상 요청의 진입과 완료를 컨트롤러 메서드 및 응답 상태와 함께 기록한다")
    void logsSuccessfulRequest(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/logged/success").param("secret", "private-value"))
                .andExpect(status().isCreated());

        assertThat(output).contains("Controller request: GET /logged/success TestController.success");
        assertThat(output).contains("Controller response: GET /logged/success TestController.success status=201");
        assertThat(output).doesNotContain("private-value");
        assertThat(output).doesNotContain("response-body");
    }

    @Test
    @DisplayName("예외가 처리된 요청도 최종 응답 상태와 함께 완료 로그를 기록한다")
    void logsHandledFailure(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/logged/failure"))
                .andExpect(status().isBadRequest());

        assertThat(output).contains("Controller request: GET /logged/failure TestController.failure");
        assertThat(output).contains("Controller response: GET /logged/failure TestController.failure status=400");
    }

    @RestController
    static class TestController {

        @GetMapping("/logged/success")
        ResponseEntity<String> success() {
            return ResponseEntity.status(201).body("response-body");
        }

        @GetMapping("/logged/failure")
        ResponseEntity<String> failure() {
            throw new IllegalArgumentException("invalid request");
        }
    }

    @RestControllerAdvice
    static class TestExceptionHandler {

        @ExceptionHandler(IllegalArgumentException.class)
        ResponseEntity<String> handle() {
            return ResponseEntity.badRequest().body("invalid request");
        }
    }
}
