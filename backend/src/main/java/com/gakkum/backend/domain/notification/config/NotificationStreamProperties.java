package com.gakkum.backend.domain.notification.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param maxLength 발행할 때 Stream 을 대략 이 개수로 잘라 Redis 메모리가 계속 늘지 않게 한다 (XADD MAXLEN ~)
 */
@Validated
@ConfigurationProperties("notification.stream")
public record NotificationStreamProperties(@NotBlank String key, @NotBlank String group,
        @Min(1) int batchSize, @NotNull Duration retryIdle, @Min(1) @DefaultValue("10000") long maxLength) {
}
