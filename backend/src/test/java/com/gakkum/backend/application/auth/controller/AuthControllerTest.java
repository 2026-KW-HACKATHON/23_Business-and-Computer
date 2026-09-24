package com.gakkum.backend.application.auth.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

import static org.mockito.Mockito.mock;

@DisplayName("학생 이메일 인증 API")
class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("KAKAO_12345", null);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("발송·검증 요청은 이메일을 정규화하고 공통 성공 응답을 반환한다")
    void sendsAndVerifiesWithCommonSuccessBody() throws Exception {
        mockMvc.perform(post("/auth/student-verification/email")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"STUDENT@KW.AC.KR\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"success\":true,\"data\":null,\"error\":null}", true));
        verify(authService).sendStudentEmailVerification("KAKAO_12345", "student@kw.ac.kr");

        mockMvc.perform(post("/auth/student-verification/email/verify")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"STUDENT@KW.AC.KR\",\"code\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"success\":true,\"data\":null,\"error\":null}", true));
        verify(authService).verifyStudentEmail("KAKAO_12345", "student@kw.ac.kr", "123456");
    }

    @Test
    @DisplayName("학교 외 이메일과 6자리 아닌 인증번호는 400으로 거부한다")
    void rejectsInvalidEmailAndCode() throws Exception {
        mockMvc.perform(post("/auth/student-verification/email")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@example.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        mockMvc.perform(post("/auth/student-verification/email/verify")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@kw.ac.kr\",\"code\":\"12345\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }
}
