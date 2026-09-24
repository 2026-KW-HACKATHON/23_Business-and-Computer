package com.gakkum.backend.application.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.domain.auth.dto.AuthCommandDto.VerifyOwnerBusinessCommand;
import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

import static org.mockito.Mockito.mock;

@DisplayName("인증 API")
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
            .andExpect(content().json("{\"success\":true}", true));
        verify(authService).sendStudentEmailVerification("KAKAO_12345", "student@kw.ac.kr");

        mockMvc.perform(post("/auth/student-verification/email/verify")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"STUDENT@KW.AC.KR\",\"code\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"success\":true}", true));
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

    @Test
    @DisplayName("사업자 인증 요청을 정규화하고 일치 여부를 공통 응답으로 반환한다")
    void verifiesOwnerBusiness() throws Exception {
        when(authService.verifyOwnerBusiness(any(VerifyOwnerBusinessCommand.class))).thenReturn(true);

        mockMvc.perform(post("/auth/owner-verification/business")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"representativeName\":\" 김사장 \",\"openedAt\":\"2020-03-01\","
                        + "\"businessNumber\":\"123-45-67890\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"success\":true,\"data\":{\"verified\":true}}", true));
        ArgumentCaptor<VerifyOwnerBusinessCommand> captor =
                ArgumentCaptor.forClass(VerifyOwnerBusinessCommand.class);
        verify(authService).verifyOwnerBusiness(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("KAKAO_12345");
        assertThat(captor.getValue().getBusinessNumber()).isEqualTo("1234567890");
        assertThat(captor.getValue().getOpenedAt()).isEqualTo(java.time.LocalDate.of(2020, 3, 1));
        assertThat(captor.getValue().getRepresentativeName()).isEqualTo("김사장");
    }

    @Test
    @DisplayName("사업자 정보가 불일치해도 정상 조회 결과에 false를 반환한다")
    void returnsFalseForOwnerBusinessMismatch() throws Exception {
        mockMvc.perform(post("/auth/owner-verification/business")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"representativeName\":\"김사장\",\"openedAt\":\"2020-03-01\","
                        + "\"businessNumber\":\"1234567890\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"success\":true,\"data\":{\"verified\":false}}", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"openedAt\":\"2020-03-01\",\"businessNumber\":\"1234567890\"}",
            "{\"representativeName\":\"김사장\",\"businessNumber\":\"1234567890\"}",
            "{\"representativeName\":\"김사장\",\"openedAt\":\"2020-03-01\"}",
            "{\"representativeName\":\"김사장\",\"openedAt\":\"2020-03-01\",\"businessNumber\":\"123456789\"}",
            "{\"representativeName\":\"김사장\",\"openedAt\":\"2020-02-30\",\"businessNumber\":\"1234567890\"}",
            "{\"representativeName\":\"김사장\",\"openedAt\":\"2999-01-01\",\"businessNumber\":\"1234567890\"}"
    })
    @DisplayName("잘못된 사업자 인증 입력은 400으로 거부한다")
    void rejectsInvalidOwnerBusinessRequest(String body) throws Exception {
        mockMvc.perform(post("/auth/owner-verification/business")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }
}
