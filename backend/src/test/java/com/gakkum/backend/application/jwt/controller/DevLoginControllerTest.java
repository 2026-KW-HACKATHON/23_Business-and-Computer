package com.gakkum.backend.application.jwt.controller;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
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

import com.gakkum.backend.application.jwt.facade.DevLoginFacade;
import com.gakkum.backend.config.SecurityConfig;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.RestAuthenticationEntryPoint;
import com.gakkum.backend.util.JWTUtil;

@DisplayName("개발용 테스트 로그인 API - 켜진 서버에서의 공개 범위와 공유 키 검증")
@WebMvcTest(controllers = DevLoginController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, DevLoginFacade.class})
@TestPropertySource(properties = {
        "dev-login.enabled=true",
        "dev-login.key=" + DevLoginControllerTest.KEY})
class DevLoginControllerTest {

    static final String KEY = "0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

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
    @DisplayName("올바른 키로 사장님을 요청하면 인증 없이 테스트 사장님의 accessToken만 반환한다")
    void issuesOwnerAccessTokenWithoutAuthentication() throws Exception {
        when(userService.getActiveUser("DEV_OWNER")).thenReturn(user("DEV_OWNER", UserRole.OWNER));
        when(jwtService.issueAccessToken("DEV_OWNER", UserRole.OWNER)).thenReturn("owner-access-token");

        mockMvc.perform(post("/dev/login")
                .header("X-Dev-Login-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"OWNER\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"accessToken\":\"owner-access-token\"}", true))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("올바른 키로 학생을 요청하면 테스트 학생의 accessToken을 반환한다")
    void issuesStudentAccessToken() throws Exception {
        when(userService.getActiveUser("DEV_STUDENT")).thenReturn(user("DEV_STUDENT", UserRole.STUDENT));
        when(jwtService.issueAccessToken("DEV_STUDENT", UserRole.STUDENT)).thenReturn("student-access-token");

        mockMvc.perform(post("/dev/login")
                .header("X-Dev-Login-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"STUDENT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("student-access-token"));
    }

    @Test
    @DisplayName("키 헤더가 없으면 401을 반환하고 토큰을 발급하지 않는다")
    void rejectsMissingKey() throws Exception {
        mockMvc.perform(post("/dev/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"OWNER\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(jwtService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-key", "0123456789abcdef0123456789abcdeX", KEY + "0"})
    @DisplayName("키가 일치하지 않으면 401을 반환하고 토큰을 발급하지 않는다")
    void rejectsWrongKey(String key) throws Exception {
        mockMvc.perform(post("/dev/login")
                .header("X-Dev-Login-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"OWNER\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(jwtService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"role\":null}", "{\"role\":\"PENDING\"}", "{\"role\":\"owner1\"}"})
    @DisplayName("role이 없거나 사장님·학생이 아니면 400을 반환한다")
    void rejectsInvalidRole(String body) throws Exception {
        mockMvc.perform(post("/dev/login")
                .header("X-Dev-Login-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("localhost 프론트 Origin에서 키 헤더를 싣는 preflight는 허용된다")
    void allowsPreflightWithKeyHeaderFromLocalhostOrigin() throws Exception {
        mockMvc.perform(options("/dev/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type, x-dev-login-key"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    containsStringIgnoringCase("x-dev-login-key")));
    }

    private User user(String username, UserRole role) {
        return User.builder()
                .id("01K6DEV0000000000000000001")
                .username(username)
                .isLock(false)
                .role(role)
                .build();
    }
}
