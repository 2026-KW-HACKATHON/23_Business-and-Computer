package com.gakkum.backend.filter;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gakkum.backend.global.logging.RequestLogContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String TID_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".tid";
    private static final String STARTED_AT_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".startedAt";

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tid = (String) request.getAttribute(TID_ATTRIBUTE);
        if (tid == null) {
            tid = UUID.randomUUID().toString().substring(0, 8);
            request.setAttribute(TID_ATTRIBUTE, tid);
        }
        MDC.put(RequestLogContext.TID_KEY, tid);
        RequestLogContext.setDepth(0);
        Long startedAt = (Long) request.getAttribute(STARTED_AT_ATTRIBUTE);
        if (startedAt == null) {
            startedAt = System.nanoTime();
            request.setAttribute(STARTED_AT_ATTRIBUTE, startedAt);
        }
        try {
            filterChain.doFilter(request, response);
            log.info("HTTP {} {} status={} {}", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), RequestLogContext.elapsed(startedAt));
        } catch (IOException | ServletException | RuntimeException | Error exception) {
            // An escaping exception may be mapped to an HTTP status later by the servlet container.
            log.info("HTTP {} {} status=unresolved {} exception={}", request.getMethod(), request.getRequestURI(),
                    RequestLogContext.elapsed(startedAt), exception.getClass().getSimpleName());
            throw exception;
        } finally {
            RequestLogContext.clear();
        }
    }
}
