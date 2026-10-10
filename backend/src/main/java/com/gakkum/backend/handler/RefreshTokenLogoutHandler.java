package com.gakkum.backend.handler;

import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.util.JWTUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * POST /logout. refreshToken 쿠키(없으면 JSON 본문의 refreshToken)의 토큰을 DB 에서 지우고,
 * 토큰이 없거나 이미 지워졌어도 쿠키는 항상 지운다. 응답 본문은 {@link ApiLogoutSuccessHandler} 가 쓴다.
 */
public class RefreshTokenLogoutHandler implements LogoutHandler {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final JwtService jwtService;
    private final JWTUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RefreshTokenLogoutHandler(JwtService jwtService, JWTUtil jwtUtil) {
        this.jwtService = jwtService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // 브라우저가 실제로 지우도록 쿠키를 만들 때와 같은 속성(Path, Secure, HttpOnly, SameSite)으로 만료시킨다
        response.addHeader(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString());

        String refreshToken = readRefreshToken(request);
        if (refreshToken == null || !jwtUtil.isValid(refreshToken, false)) {
            return;
        }

        // Refresh 토큰 삭제 (whitelist 에서 빠지면 /refresh 와 /jwt/exchange 가 거부한다)
        jwtService.removeRefresh(refreshToken);
    }

    private String readRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (REFRESH_COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return readRefreshTokenFromBody(request);
    }

    // 예전 방식(본문 { "refreshToken": "..." })도 받는다. 본문을 읽지 못하면 토큰이 없는 것으로 본다
    private String readRefreshTokenFromBody(HttpServletRequest request) {
        try {
            String body = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            if (!StringUtils.hasText(body)) {
                return null;
            }
            JsonNode refreshToken = objectMapper.readTree(body).get(REFRESH_COOKIE_NAME);
            return refreshToken != null && refreshToken.isString() && StringUtils.hasText(refreshToken.asString())
                    ? refreshToken.asString()
                    : null;
        } catch (IOException | JacksonException e) {
            return null;
        }
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .path("/")
                .sameSite("None")
                .httpOnly(true)
                .secure(true)
                .maxAge(0)
                .build();
    }
}
