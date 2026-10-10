package com.gakkum.backend.domain.notification.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.gakkum.backend.domain.notification.config.NotificationStreamProperties;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;
import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;

class NotificationEventPublisherTest {

    private final StringRedisTemplate template = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, String, String> streams = mock(StreamOperations.class);
    private final NotificationEvent event = new NotificationEvent(UUID.randomUUID(), "01K58M6PJV8VAJMXHBHJ2PNB5C",
            NotificationType.JOB_APPLICATION_RECEIVED, "새로운 지원자가 있어요", "새 지원이 도착했습니다.",
            NotificationTargetType.JOB, "42");

    @Test
    @DisplayName("커밋 후 Redis 발행이 실패해도 완료된 지원을 실패로 바꾸지 않고 재시도하지 않는다")
    void doesNotFailCompletedBusinessWhenRedisIsUnavailable() {
        when(template.<String, String>opsForStream()).thenReturn(streams);
        when(streams.add(eq("notification-events"), anyMap(), any(XAddOptions.class)))
                .thenThrow(new RedisConnectionFailureException("테스트 Redis 장애"));
        var publisher = new NotificationEventPublisher(template, properties(10000));

        assertThatCode(() -> publisher.publishCommitted(event)).doesNotThrowAnyException();
        verify(streams).add(eq("notification-events"), eq(event.toMap()), any(XAddOptions.class));
    }

    @Test
    @DisplayName("발행할 때 Stream을 설정한 개수로 근사 트리밍(MAXLEN ~)해 무한히 쌓이지 않게 한다")
    void trimsStreamApproximatelyOnPublish() {
        when(template.<String, String>opsForStream()).thenReturn(streams);
        when(streams.add(eq("notification-events"), anyMap(), any(XAddOptions.class)))
                .thenReturn(RecordId.of("1-0"));
        var publisher = new NotificationEventPublisher(template, properties(10000));

        assertThat(publisher.publish(event)).isEqualTo(RecordId.of("1-0"));

        ArgumentCaptor<XAddOptions> options = ArgumentCaptor.forClass(XAddOptions.class);
        verify(streams).add(eq("notification-events"), eq(event.toMap()), options.capture());
        assertThat(options.getValue().getMaxlen()).isEqualTo(10000L);
        assertThat(options.getValue().isApproximateTrimming()).isTrue();
        assertThat(options.getValue().isNoMkStream()).isFalse();
    }

    private NotificationStreamProperties properties(long maxLength) {
        return new NotificationStreamProperties("notification-events", "notification-persistence", 100,
                Duration.ofSeconds(30), maxLength);
    }
}
