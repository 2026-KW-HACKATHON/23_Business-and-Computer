package com.gakkum.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ControllerLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ControllerLoggingInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod
                && handlerMethod.getBeanType().isAnnotationPresent(RestController.class)) {
            log.info("Controller request: {} {} {}.{}", request.getMethod(), request.getRequestURI(),
                    handlerMethod.getBeanType().getSimpleName(), handlerMethod.getMethod().getName());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        if (handler instanceof HandlerMethod handlerMethod
                && handlerMethod.getBeanType().isAnnotationPresent(RestController.class)) {
            log.info("Controller response: {} {} {}.{} status={}", request.getMethod(), request.getRequestURI(),
                    handlerMethod.getBeanType().getSimpleName(), handlerMethod.getMethod().getName(),
                    response.getStatus());
        }
    }
}
