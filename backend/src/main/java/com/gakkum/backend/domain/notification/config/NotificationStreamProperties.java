package com.gakkum.backend.domain.notification.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("notification.stream")
public record NotificationStreamProperties(@NotBlank String key, @NotBlank String group,
        @Min(1) int batchSize, @NotNull Duration retryIdle) {
}
