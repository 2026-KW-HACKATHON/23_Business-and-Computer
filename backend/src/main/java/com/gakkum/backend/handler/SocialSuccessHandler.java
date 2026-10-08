package com.gakkum.backend.handler;

import com.gakkum.backend.config.FrontendOrigins;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.util.JWTUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Qualifier("SocialSuccessHandler")
@RequiredArgsConstructor
public class SocialSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final JWTUtil jwtUtil;
    private final FrontendOrigins frontendOrigins;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // username, role
        String username =  authentication.getName();
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        // JWT(Refresh) 발급
        String refreshToken = jwtUtil.createJWT(username, role, false);

        // 발급한 Refresh DB 테이블 저장 (Refresh whitelist)
        jwtService.addRefresh(username, refreshToken);

        // 응답
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .path("/")
                .sameSite("None")
                .httpOnly(true)
                .secure(true)
                .maxAge(60)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // 로그인을 시작한 프론트(LoginOriginFilter 가 세션에 담음)로 돌려보내고, 모르면 기본 프론트로 보낸다
        response.sendRedirect(frontendOrigins.resolve(takeLoginOrigin(request)) + "/cookie");
    }

    private String takeLoginOrigin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object origin = session.getAttribute(FrontendOrigins.LOGIN_ORIGIN_SESSION_KEY);
        session.removeAttribute(FrontendOrigins.LOGIN_ORIGIN_SESSION_KEY);
        return origin instanceof String value ? value : null;
    }

}