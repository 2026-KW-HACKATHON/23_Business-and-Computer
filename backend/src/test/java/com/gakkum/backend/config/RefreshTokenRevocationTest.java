package com.gakkum.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.ResultMatcher;

import com.gakkum.backend.application.jwt.controller.JwtController;
import com.gakkum.backend.domain.jwt.entity.RefreshToken;
import com.gakkum.backend.domain.jwt.repository.RefreshRepository;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.RestAuthenticationEntryPoint;
import com.gakkum.backend.util.JWTUtil;

/**
 * 실제 JwtService · JWTUtil 과 보안 설정으로 로그아웃과 토큰 교환이 DB whitelist(refresh_tokens)를 따르는지 확인한다.
 * RefreshRepository 는 메모리 집합으로 흉내 낸다.
 */
@DisplayName("로그아웃 · 토큰 교환 - refresh token 폐기")
@WebMvcTest(controllers = JwtController.class)
@Import({SecurityConfig.class, FrontendOrigins.class, RestAuthenticationEntryPoint.class, JwtService.class,
        JWTUtil.class})
@TestPropertySource(properties = {
        "demo-login.enabled=false",
        "jwt.secret=test-secret-key-for-refresh-token-revocation-0123456789",
        "jwt.access-token-expiration=600000",
        "jwt.refresh-token-expiration=604800000"})
class RefreshTokenRevocationTest {

    private static final String USERNAME = "KAKAO_12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTUtil jwtUtil;

    @MockitoBean
    private RefreshRepository refreshRepository;

    @MockitoBean
    private UserService userService;

    @MockitoBean(name = "SocialSuccessHandler")
    private AuthenticationSuccessHandler socialSuccessHandler;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    /** refresh_tokens 테이블 대신 쓰는 whitelist */
    private final Set<String> whitelist = new HashSet<>();

    @BeforeEach
    void fakeRefreshTable() {
        when(refreshRepository.existsByRefresh(anyString()))
                .thenAnswer(invocation -> whitelist.contains(invocation.<String>getArgument(0)));
        doAnswer(invocation -> whitelist.remove(invocation.<String>getArgument(0)))
                .when(refreshRepository).deleteByRefresh(anyString());
        when(refreshRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            whitelist.add(token.getRefresh());
            return token;
        });
    }

    @Test
    @DisplayName("DB에 있는 refreshToken 쿠키는 교환되고 새 refreshToken 쿠키를 내려준다")
    void exchangesWhitelistedRefreshToken() throws Exception {
        String refreshToken = loggedInRefreshToken();

        mockMvc.perform(post("/jwt/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie("refreshToken", refreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                    containsString("refreshToken=ey"), containsString("Max-Age=604800"))));

        assertThat(whitelist).singleElement().satisfies(token -> assertThat(jwtUtil.isValid(token, false)).isTrue());
    }

    @Test
    @DisplayName("서명은 맞지만 DB에서 지워진 refreshToken 쿠키는 교환을 401로 거부하고 새 쿠키를 주지 않는다")
    void rejectsExchangeOfDeletedRefreshToken() throws Exception {
        String refreshToken = jwtUtil.createJWT(USERNAME, "ROLE_STUDENT", false);

        mockMvc.perform(post("/jwt/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie("refreshToken", refreshToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(refreshRepository, never()).save(any());
    }

    @Test
    @DisplayName("access token 없이 refreshToken 쿠키로 로그아웃하면 DB에서 지우고 쿠키를 지우는 응답과 공통 성공 응답을 준다")
    void logoutWithCookieDeletesTokenAndClearsCookie() throws Exception {
        String refreshToken = loggedInRefreshToken();

        mockMvc.perform(post("/logout").cookie(new Cookie("refreshToken", refreshToken)))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"success\":true}", true))
            .andExpect(clearsRefreshCookie());

        assertThat(whitelist).isEmpty();
        verify(refreshRepository).deleteByRefresh(refreshToken);
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃해도 200과 쿠키를 지우는 응답을 주고 DB는 건드리지 않는다")
    void logoutWithoutCookieIsIdempotent() throws Exception {
        mockMvc.perform(post("/logout"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"success\":true}", true))
            .andExpect(clearsRefreshCookie());

        verify(refreshRepository, never()).deleteByRefresh(anyString());
    }

    @Test
    @DisplayName("쿠키가 없으면 예전 방식처럼 JSON 본문의 refreshToken으로 로그아웃한다")
    void logoutFallsBackToJsonBody() throws Exception {
        String refreshToken = loggedInRefreshToken();

        mockMvc.perform(post("/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andExpect(clearsRefreshCookie());

        assertThat(whitelist).isEmpty();
    }

    @Test
    @DisplayName("본문이 JSON이 아니어도 로그아웃은 200과 쿠키를 지우는 응답을 준다")
    void logoutIgnoresMalformedBody() throws Exception {
        mockMvc.perform(post("/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not-json"))
            .andExpect(status().isOk())
            .andExpect(clearsRefreshCookie());

        verify(refreshRepository, never()).deleteByRefresh(anyString());
    }

    @Test
    @DisplayName("로그아웃한 뒤 이전 refreshToken 쿠키로 /refresh 와 /jwt/exchange 를 부르면 401을 반환한다")
    void oldCookieIsRejectedAfterLogout() throws Exception {
        String refreshToken = loggedInRefreshToken();
        Cookie oldCookie = new Cookie("refreshToken", refreshToken);

        mockMvc.perform(post("/logout").cookie(oldCookie))
            .andExpect(status().isOk());

        mockMvc.perform(post("/refresh").cookie(oldCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mockMvc.perform(post("/jwt/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(oldCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("GET /logout 은 로그아웃하지 않고 인증이 필요한 요청으로 401을 반환한다")
    void getLogoutDoesNotLogOut() throws Exception {
        String refreshToken = loggedInRefreshToken();

        mockMvc.perform(get("/logout").cookie(new Cookie("refreshToken", refreshToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(whitelist).containsExactly(refreshToken);
    }

    @Test
    @DisplayName("허용된 프론트 출처에서 credentials 와 함께 로그아웃하면 CORS 허용 헤더를 준다")
    void logoutAllowsFrontendOriginWithCredentials() throws Exception {
        mockMvc.perform(post("/logout")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .cookie(new Cookie("refreshToken", loggedInRefreshToken())))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    /** 로그인(소셜 로그인 · 가입 · 데모 로그인)처럼 발급하고 DB에 저장한 refresh token */
    private String loggedInRefreshToken() {
        String refreshToken = jwtUtil.createJWT(USERNAME, "ROLE_STUDENT", false);
        whitelist.add(refreshToken);
        return refreshToken;
    }

    private ResultMatcher clearsRefreshCookie() {
        return header().string(HttpHeaders.SET_COOKIE, allOf(
                containsString("refreshToken=;"),
                containsString("Path=/"),
                containsString("Max-Age=0"),
                containsString("Secure"),
                containsString("HttpOnly"),
                containsString("SameSite=None")));
    }
}
