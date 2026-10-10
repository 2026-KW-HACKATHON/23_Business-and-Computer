package com.gakkum.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * 소셜 로그인 중에만 쓰는 세션 쿠키(JSESSIONID)의 속성이 application.yaml 에서 Spring Boot 서버 설정으로 그대로 묶이는지 확인한다.
 */
@DisplayName("세션 쿠키 속성 설정")
class SessionCookieConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("spring.config.location=classpath:application.yaml")
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ServerPropertiesConfiguration.class);

    @Test
    @DisplayName("세션 쿠키는 HttpOnly · Secure · SameSite=Lax 로 내려간다")
    void sessionCookieIsHttpOnlySecureAndLax() {
        runner.run(context -> {
            Cookie cookie = context.getBean(ServerProperties.class).getServlet().getSession().getCookie();

            assertThat(cookie.getHttpOnly()).isTrue();
            assertThat(cookie.getSecure()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo(Cookie.SameSite.LAX);
        });
    }

    @Test
    @DisplayName("http 로 IP 주소에 접속하는 로컬 개발에서는 환경변수로 Secure 만 끌 수 있다")
    void secureCanBeTurnedOffByEnvironment() {
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("test-systemEnvironment",
                        Map.of("SERVER_SERVLET_SESSION_COOKIE_SECURE", "false"))))
                .run(context -> {
            Cookie cookie = context.getBean(ServerProperties.class).getServlet().getSession().getCookie();

            assertThat(cookie.getSecure()).isFalse();
            assertThat(cookie.getHttpOnly()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo(Cookie.SameSite.LAX);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ServerProperties.class)
    static class ServerPropertiesConfiguration {
    }
}
