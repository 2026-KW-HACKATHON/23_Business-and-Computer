package com.gakkum.backend.filter;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 이미 보내기 시작한 응답에는 오류 응답을 만들지 않는다.
 * 파일 전송이 중간에 실패하면 컨테이너가 오류 페이지(/error)를 응답 뒤에 이어 붙인 다음 연결을 끊는데,
 * 이어 붙이는 처리를 건너뛰어 받는 쪽 파일에 오류 본문이 섞이지 않게 한다. 연결은 그대로 끊긴다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CommittedResponseErrorFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (response.isCommitted() && request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) != null) {
            return;
        }
        filterChain.doFilter(request, response);
    }
}
