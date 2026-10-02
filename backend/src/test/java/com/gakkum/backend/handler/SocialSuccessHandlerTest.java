package com.gakkum.backend.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.util.JWTUtil;

class SocialSuccessHandlerTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JWTUtil jwtUtil = mock(JWTUtil.class);
    private final SocialSuccessHandler handler = new SocialSuccessHandler(jwtService, jwtUtil);

    @Test
    @DisplayName("소셜 로그인 성공 시 60초짜리 크로스 사이트 refreshToken 쿠키를 내려주고 프론트로 이동한다")
    void setsCrossSiteRefreshTokenCookieAndRedirects() throws Exception {
        when(jwtUtil.createJWT("KAKAO_12345", "ROLE_PENDING", false)).thenReturn("refresh-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                new UsernamePasswordAuthenticationToken("KAKAO_12345", null,
                        List.of(new SimpleGrantedAuthority("PENDING"))));

        verify(jwtService).addRefresh("KAKAO_12345", "refresh-token");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).singleElement().asString()
                .contains("refreshToken=refresh-token", "Path=/", "Max-Age=60;", "Secure", "HttpOnly", "SameSite=None")
                .doesNotContain("Domain");
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/cookie");
    }
}
