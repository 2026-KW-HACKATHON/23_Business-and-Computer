package com.gakkum.backend.application.demo.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.gakkum.backend.application.demo.dto.DemoLoginResponse;
import com.gakkum.backend.application.demo.dto.DemoRole;
import com.gakkum.backend.application.demo.facade.DemoFacade;
import com.gakkum.backend.config.SecurityConfig;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.exception.RestAuthenticationEntryPoint;
import com.gakkum.backend.util.JWTUtil;

@DisplayName("데모 로그인 API - 켜진 서버에서 키 없이 공개되는 범위와 응답 모양")
@WebMvcTest(controllers = DemoController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class})
@TestPropertySource(properties = "demo-login.enabled=true")
class DemoControllerTest {

    private static final String SESSION = "01K6DEMO00000000000000000A";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoFacade demoFacade;

    @MockitoBean
    private JWTUtil jwtUtil;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    @MockitoBean(name = "SocialSuccessHandler")
    private AuthenticationSuccessHandler socialSuccessHandler;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    @DisplayName("인증과 키 없이 역할만 보내면 accessToken과 demoSessionId만 본문에 담고 refresh 토큰은 쿠키로 내려준다")
    void logsInWithoutAuthenticationOrKey() throws Exception {
        when(demoFacade.login(argThat(request ->
                request.getRole() == DemoRole.OWNER && request.getDemoSessionId() == null)))
                .thenReturn(DemoLoginResponse.of("access-token", "refresh-token", SESSION));

        mockMvc.perform(post("/demo/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"OWNER\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json(
                    "{\"accessToken\":\"access-token\",\"demoSessionId\":\"" + SESSION + "\"}", true))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                    containsString("refreshToken=refresh-token"),
                    containsString("Path=/"),
                    containsString("Max-Age=604800"),
                    containsString("Secure"),
                    containsString("HttpOnly"),
                    containsString("SameSite=None"))));
    }

    @Test
    @DisplayName("받아 둔 demoSessionId를 함께 보내면 그 값과 요청한 역할을 그대로 넘긴다")
    void passesDemoSessionIdForRoleSwitch() throws Exception {
        when(demoFacade.login(any())).thenReturn(DemoLoginResponse.of("student-token", "refresh-token", SESSION));

        mockMvc.perform(post("/demo/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"STUDENT\",\"demoSessionId\":\"" + SESSION + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("student-token"))
            .andExpect(jsonPath("$.demoSessionId").value(SESSION));

        verify(demoFacade).login(argThat(request ->
                request.getRole() == DemoRole.STUDENT && SESSION.equals(request.getDemoSessionId())));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"role\":null}",
            "{\"role\":\"PENDING\"}",
            "{\"role\":\"owner1\"}",
            "{\"role\":\"OWNER\",\"demoSessionId\":\"\"}",
            "{\"role\":\"OWNER\",\"demoSessionId\":\"short\"}",
            "{\"role\":\"OWNER\",\"demoSessionId\":\"01k6demo00000000000000000a\"}"})
    @DisplayName("role이 사장님·학생이 아니거나 demoSessionId 형식이 틀리면 400을 반환하고 로그인하지 않는다")
    void rejectsInvalidBody(String body) throws Exception {
        mockMvc.perform(post("/demo/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(demoFacade);
    }

    @Test
    @DisplayName("모르는 세션은 401, 생성 상한 초과는 429를 공통 오류 형식으로 반환하고 쿠키를 내려주지 않는다")
    void returnsFacadeErrorsInCommonFormat() throws Exception {
        when(demoFacade.login(any())).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));
        mockMvc.perform(post("/demo/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"OWNER\",\"demoSessionId\":\"" + SESSION + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        doThrow(new BusinessException(ErrorCode.DEMO_SESSION_LIMIT_EXCEEDED)).when(demoFacade).login(any());
        mockMvc.perform(post("/demo/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"OWNER\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("DEMO_429"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("localhost 프론트 Origin의 preflight는 credentials와 함께 허용된다")
    void allowsPreflightFromLocalhostOrigin() throws Exception {
        mockMvc.perform(options("/demo/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }
}
