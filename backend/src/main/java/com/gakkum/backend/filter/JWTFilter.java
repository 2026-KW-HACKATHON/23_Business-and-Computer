package com.gakkum.backend.filter;

import com.gakkum.backend.util.JWTUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JWTUtil jwtUtil;
    // 파일 전송처럼 응답을 비동기로 마무리하는 요청은 같은 요청이 한 번 더 필터 체인을 지나는데, 이 필터는 그때 다시 실행되지 않는다.
    // 인증 정보를 요청 속성에 남겨 그 디스패치에서도 같은 사용자로 인가되게 한다 (세션에는 저장하지 않는다)
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // "Bearer " 접두사가 없거나 그 뒤가 비어 있는 헤더도 유효하지 않은 토큰과 같은 401로 거부한다
        if (!authorization.startsWith(BEARER_PREFIX)) {
            rejectInvalidToken(response);
            return;
        }

        // 토큰 파싱
        String accessToken = authorization.substring(BEARER_PREFIX.length());

        if (!accessToken.isEmpty() && jwtUtil.isValid(accessToken, true)) {

            String username = jwtUtil.getUsername(accessToken);
            String role = jwtUtil.getRole(accessToken);

            List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(role));

            Authentication auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            filterChain.doFilter(request, response);

        } else {
            rejectInvalidToken(response);
        }

    }

    private void rejectInvalidToken(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"토큰 만료 또는 유효하지 않은 토큰\"}");
    }

}