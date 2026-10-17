package com.gakkum.backend.filter;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gakkum.backend.config.FrontendOrigins;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * refreshToken 쿠키로 처리하는 POST /refresh · /logout · /jwt/exchange 와 쿠키를 새로 심는 POST /demo/login 은
 * 허용 목록(FrontendOrigins)의 프론트에서 보낸 요청만 받는다.
 * 쿠키가 SameSite=None 이라 다른 사이트가 보낸 요청에도 붙으므로, 그 사이트가 로그아웃시키거나 토큰을 바꾸거나
 * 방문자를 데모 계정으로 로그인시키지 못하게 막는다.
 * Origin 을 먼저 보고, 없으면 Referer 의 주소를 본다. 허용 목록에 없으면 토큰 · 쿠키를 건드리지 않고 403 COMMON_403 을 준다.
 * 둘 다 없으면 통과시킨다. 브라우저는 다른 사이트에서 보내는 POST 에 Origin 을 꼭 붙이고, 브라우저가 아닌 곳은 사용자 쿠키를 갖고 있지 않다.
 * 시큐리티 필터 체인 안에서만 쓰도록 빈으로 등록하지 않는다 (SecurityConfig 에서 만든다).
 */
public class RefreshCookieOriginFilter extends OncePerRequestFilter {

    private static final RequestMatcher REFRESH_COOKIE_REQUESTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/refresh"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/logout"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/jwt/exchange"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/demo/login"));

    private final FrontendOrigins frontendOrigins;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RefreshCookieOriginFilter(FrontendOrigins frontendOrigins) {
        this.frontendOrigins = frontendOrigins;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !REFRESH_COOKIE_REQUESTS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (origin == null && referer == null) {
            filterChain.doFilter(request, response);
            return;
        }
        // 읽을 수 없는 Referer 도 허용 목록에 없는 것으로 본다
        String source = origin != null ? origin : FrontendOrigins.originOf(referer);
        if (frontendOrigins.isAllowed(source)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(ErrorCode.ORIGIN_NOT_ALLOWED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(ErrorCode.ORIGIN_NOT_ALLOWED));
    }
}
