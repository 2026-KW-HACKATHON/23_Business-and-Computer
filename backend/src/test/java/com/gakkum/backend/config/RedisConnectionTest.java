package com.gakkum.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@DisplayName("Redis 자동 설정과 연결")
class RedisConnectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.config.location=classpath:application.yaml")
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class));

    @Test
    @DisplayName("로컬 접속 설정으로 Lettuce 연결 팩토리와 문자열 템플릿을 생성한다")
    void configuresLocalConnection() {
        contextRunner.withPropertyValues(
                "REDIS_HOST=localhost", "REDIS_PORT=6379",
                "REDIS_USERNAME=", "REDIS_PASSWORD=", "REDIS_SSL_ENABLED=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(StringRedisTemplate.class);
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    assertThat(factory.getHostName()).isEqualTo("localhost");
                    assertThat(factory.getPort()).isEqualTo(6379);
                    assertThat(factory.getStandaloneConfiguration().getPassword().isPresent()).isFalse();
                });
    }

    @Test
    @DisplayName("개발 서버의 주소·포트·인증·TLS 설정을 외부 값으로 바꿀 수 있다")
    void configuresExternalConnection() {
        contextRunner.withPropertyValues(
                "REDIS_HOST=redis.internal", "REDIS_PORT=6380",
                "REDIS_USERNAME=notification", "REDIS_PASSWORD=test-password", "REDIS_SSL_ENABLED=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    assertThat(factory.getHostName()).isEqualTo("redis.internal");
                    assertThat(factory.getPort()).isEqualTo(6380);
                    assertThat(factory.getStandaloneConfiguration().getUsername()).isEqualTo("notification");
                    assertThat(factory.getStandaloneConfiguration().getPassword().get())
                            .isEqualTo("test-password".toCharArray());
                    assertThat(factory.getClientConfiguration().isUseSsl()).isTrue();
                });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
    @DisplayName("실제 Redis에 연결해 PING 요청에 PONG을 받는다")
    void pingsRedis() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            StringRedisTemplate template = context.getBean(StringRedisTemplate.class);
            try (RedisConnection connection = template.getConnectionFactory().getConnection()) {
                assertThat(connection.ping()).isEqualTo("PONG");
            }
        });
    }
}
