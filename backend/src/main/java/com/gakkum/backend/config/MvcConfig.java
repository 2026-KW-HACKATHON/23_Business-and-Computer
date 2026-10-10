package com.gakkum.backend.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class MvcConfig implements WebMvcConfigurer {

    // JPA 없이 웹 계층만 띄우는 테스트에는 빈이 없다
    private final ObjectProvider<OpenEntityManagerInViewInterceptor> openEntityManagerInViewInterceptor;

    /**
     * OSIV는 요청이 끝날 때까지 DB 커넥션을 쥔다. 전송이 오래 이어지는 ZIP 다운로드만 빼서,
     * 권한과 파일 조회가 끝나면 커넥션을 돌려주고 전송하게 한다. 나머지 경로는 Spring Boot 기본과 같다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        openEntityManagerInViewInterceptor.ifAvailable(interceptor -> registry.addWebRequestInterceptor(interceptor)
                .excludePathPatterns("/jobs/submissions/download"));
    }

}
