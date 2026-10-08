package com.gakkum.backend.filter;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gakkum.backend.config.FrontendOrigins;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 소셜 로그인을 시작한 프론트 주소를 세션에 담아 두어, 로그인이 끝나면 그 프론트로 돌려보내게 한다.
 * 주소는 redirect_origin 파라미터를 먼저 보고, 없으면 Referer 에서 읽는다. 허용 목록에 없는 주소는 담지 않는다.
 * 세션은 OAuth2 인가 요청을 담는 세션과 같아서 카카오를 다녀와도 그대로 남는다.
 * 시큐리티 필터 체인 안에서만 쓰도록 빈으로 등록하지 않는다 (SecurityConfig 에서 만든다).
 */
public class LoginOriginFilter extends OncePerRequestFilter {

    public static final String REDIRECT_ORIGIN_PARAMETER = "redirect_origin";
    private static final String AUTHORIZATION_PATH_PREFIX = "/oauth2/authorization/";

    private final FrontendOrigins frontendOrigins;

    public LoginOriginFilter(FrontendOrigins frontendOrigins) {
        this.frontendOrigins = frontendOrigins;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(AUTHORIZATION_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String origin = FrontendOrigins.originOf(request.getParameter(REDIRECT_ORIGIN_PARAMETER));
        if (origin == null) {
            origin = FrontendOrigins.originOf(request.getHeader(HttpHeaders.REFERER));
        }
        if (frontendOrigins.isAllowed(origin)) {
            request.getSession().setAttribute(FrontendOrigins.LOGIN_ORIGIN_SESSION_KEY, origin);
        }
        filterChain.doFilter(request, response);
    }
}
