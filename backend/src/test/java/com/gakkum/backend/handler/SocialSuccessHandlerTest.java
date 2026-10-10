package com.gakkum.backend.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.gakkum.backend.config.FrontendOrigins;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.util.JWTUtil;

class SocialSuccessHandlerTest {

    private static final String DEPLOYED = "https://gakkum.hubspacekw.com";
    private static final String LOCAL = "http://localhost:5173";

    private final JwtService jwtService = mock(JwtService.class);
    private final JWTUtil jwtUtil = mock(JWTUtil.class);
    private final FrontendOrigins frontendOrigins = new FrontendOrigins(List.of(LOCAL, DEPLOYED), DEPLOYED);
    private final SocialSuccessHandler handler = new SocialSuccessHandler(jwtService, jwtUtil, frontendOrigins);
    private final Authentication authentication = new UsernamePasswordAuthenticationToken("KAKAO_12345", null,
            List.of(new SimpleGrantedAuthority("ROLE_PENDING")));

    @BeforeEach
    void givenRefreshToken() {
        when(jwtUtil.createJWT("KAKAO_12345", "ROLE_PENDING", false)).thenReturn("refresh-token");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("소셜 로그인 성공 뒤 세션에 저장된 인증을 지우고 세션을 끝내며, 쿠키와 이동 주소는 그대로다")
    void invalidatesSessionHoldingAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        session.setAttribute(FrontendOrigins.LOGIN_ORIGIN_SESSION_KEY, LOCAL);
        // oauth2Login 이 성공 핸들러를 부르기 전에 세션과 SecurityContextHolder 에 인증을 저장한 상태
        SecurityContext context = new SecurityContextImpl(authentication);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        SecurityContextHolder.setContext(context);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(session.isInvalid()).isTrue();
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).singleElement().asString()
                .contains("refreshToken=refresh-token", "Path=/", "Max-Age=60;", "Secure", "HttpOnly", "SameSite=None");
        assertThat(response.getRedirectedUrl()).isEqualTo(LOCAL + "/cookie");
    }

    @Test
    @DisplayName("소셜 로그인 성공 시 60초짜리 크로스 사이트 refreshToken 쿠키를 내려주고 로그인을 시작한 프론트로 이동한다")
    void setsCrossSiteRefreshTokenCookieAndRedirectsToStartingFrontend() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(FrontendOrigins.LOGIN_ORIGIN_SESSION_KEY, LOCAL);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(jwtService).addRefresh("KAKAO_12345", "refresh-token");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).singleElement().asString()
                .contains("refreshToken=refresh-token", "Path=/", "Max-Age=60;", "Secure", "HttpOnly", "SameSite=None")
                .doesNotContain("Domain");
        assertThat(response.getRedirectedUrl()).isEqualTo(LOCAL + "/cookie");
        assertThat(request.getSession().getAttribute(FrontendOrigins.LOGIN_ORIGIN_SESSION_KEY)).isNull();
    }

    @Test
    @DisplayName("로그인을 시작한 프론트를 모르면 기본 프론트(배포 사이트)로 이동한다")
    void redirectsToDefaultFrontendWithoutStartingOrigin() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(DEPLOYED + "/cookie");
    }

    @Test
    @DisplayName("세션에 허용 목록 밖의 주소가 있어도 그 주소로 보내지 않고 기본 프론트로 이동한다")
    void ignoresStartingOriginOutsideAllowList() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(FrontendOrigins.LOGIN_ORIGIN_SESSION_KEY, "https://evil.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(DEPLOYED + "/cookie");
    }
}
