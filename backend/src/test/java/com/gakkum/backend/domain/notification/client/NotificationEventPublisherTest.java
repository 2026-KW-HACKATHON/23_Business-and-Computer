package com.gakkum.backend.domain.notification.client;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.gakkum.backend.domain.notification.config.NotificationStreamProperties;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;
import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;

class NotificationEventPublisherTest {

    @Test
    @DisplayName("커밋 후 Redis 발행이 실패해도 완료된 지원을 실패로 바꾸지 않고 재시도하지 않는다")
    void doesNotFailCompletedBusinessWhenRedisIsUnavailable() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        when(template.<String, String>opsForStream()).thenReturn(streams);
        when(streams.add(eq("notification-events"), anyMap()))
                .thenThrow(new RedisConnectionFailureException("테스트 Redis 장애"));
        var publisher = new NotificationEventPublisher(template,
                new NotificationStreamProperties("notification-events", "notification-persistence", 100,
                        Duration.ofSeconds(30)));
        var event = new NotificationEvent(UUID.randomUUID(), "01K58M6PJV8VAJMXHBHJ2PNB5C",
                NotificationType.JOB_APPLICATION_RECEIVED, "새로운 지원자가 있어요", "새 지원이 도착했습니다.",
                NotificationTargetType.JOB, "42");

        assertThatCode(() -> publisher.publishCommitted(event)).doesNotThrowAnyException();
        verify(streams).add("notification-events", event.toMap());
    }
}
