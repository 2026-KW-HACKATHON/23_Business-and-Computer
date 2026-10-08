package com.gakkum.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

/**
 * OSIV 인터셉터를 직접 빈으로 둔다. 이 빈이 있으면 Spring Boot는 모든 경로에 거는 기본 등록을 하지 않고,
 * MvcConfig가 적용할 경로를 정해 등록한다. spring.jpa.open-in-view=false면 Spring Boot 기본과 같이 OSIV를 쓰지 않는다.
 */
@Configuration
@ConditionalOnProperty(name = "spring.jpa.open-in-view", havingValue = "true", matchIfMissing = true)
public class OpenEntityManagerInViewConfig {

    @Bean
    public OpenEntityManagerInViewInterceptor openEntityManagerInViewInterceptor() {
        return new OpenEntityManagerInViewInterceptor();
    }
}
