package com.gakkum.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import com.gakkum.backend.domain.notification.client.NotificationStreamConsumer;
import com.gakkum.backend.domain.notification.service.NotificationService;

@DisplayName("알림 Stream Consumer 활성화 설정")
class NotificationStreamConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("spring.config.location=classpath:application.yaml")
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(NotificationStreamConfig.class)
            .withBean(NotificationService.class, () -> mock(NotificationService.class));

    @Test
    @DisplayName("Consumer가 꺼져 있으면 Redis 연결 없이 기동하고 스케줄러를 생성하지 않는다")
    void leavesConsumerDisabled() {
        runner.withPropertyValues("NOTIFICATION_STREAM_ENABLED=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(NotificationStreamConsumer.class)
                    .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }

    @Test
    @DisplayName("Consumer를 활성화하면 스케줄러가 자동으로 Stream을 폴링한다")
    void schedulesEnabledConsumer() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        when(template.<String, String>opsForStream()).thenReturn(streams);
        CountDownLatch polled = new CountDownLatch(1);
        doAnswer(invocation -> {
            polled.countDown();
            return "OK";
        }).when(streams).createGroup(anyString(), any(ReadOffset.class), anyString());

        runner.withBean(StringRedisTemplate.class, () -> template)
                .withPropertyValues("NOTIFICATION_STREAM_ENABLED=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(NotificationStreamConsumer.class);
                    assertThat(polled.await(5, TimeUnit.SECONDS)).isTrue();
                });
    }
}
