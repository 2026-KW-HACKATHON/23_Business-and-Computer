package com.gakkum.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.gakkum.backend.domain.notification.client.NotificationStreamConsumer;
import com.gakkum.backend.domain.notification.config.NotificationStreamProperties;
import com.gakkum.backend.domain.notification.service.NotificationService;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationStreamProperties.class)
public class NotificationStreamConfig {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "notification.stream", name = "enabled", havingValue = "true")
    @EnableScheduling
    static class ConsumerConfiguration {

        @Bean
        NotificationStreamConsumer notificationStreamConsumer(StringRedisTemplate redisTemplate,
                NotificationService notificationService, NotificationStreamProperties properties) {
            return new NotificationStreamConsumer(redisTemplate, notificationService, properties);
        }
    }
}
