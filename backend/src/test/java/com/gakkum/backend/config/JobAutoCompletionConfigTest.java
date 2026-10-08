package com.gakkum.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.application.job.scheduler.JobAutoCompletionScheduler;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.notification.client.NotificationStreamConsumer;
import com.gakkum.backend.domain.notification.service.NotificationService;

@DisplayName("미확인 제출물 자동 완료 스케줄러 활성화 설정")
class JobAutoCompletionConfigTest {

    private final JobService jobService = mock(JobService.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JobAutoCompletionConfig.class, ClockConfig.class)
            .withBean(JobService.class, () -> jobService)
            .withBean(JobFacade.class, () -> mock(JobFacade.class));

    private final ApplicationContextRunner configuredRunner = runner
            .withUserConfiguration(NotificationStreamConfig.class)
            .withBean(NotificationService.class, () -> mock(NotificationService.class))
            .withPropertyValues("spring.config.location=classpath:application.yaml")
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    @DisplayName("알림 Consumer가 꺼져 있어도 자동 완료 스케줄러는 등록되고 기동 직후 한 번 실행한다")
    void runsOnStartupWithoutConsumer() {
        CountDownLatch executed = givenExecutionLatch();

        configuredRunner
                .withPropertyValues("NOTIFICATION_STREAM_ENABLED=false", "JOB_AUTO_COMPLETION_ENABLED=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(JobAutoCompletionScheduler.class)
                            .doesNotHaveBean(NotificationStreamConsumer.class);
                    assertThat(executed.await(5, TimeUnit.SECONDS)).isTrue();
                });
    }

    @Test
    @DisplayName("설정 값이 없으면 자동 완료 스케줄러를 기본으로 활성화하고 기본 간격으로 등록한다")
    void enablesSchedulerByDefault() {
        CountDownLatch executed = givenExecutionLatch();

        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JobAutoCompletionScheduler.class);
            assertThat(executed.await(5, TimeUnit.SECONDS)).isTrue();
        });
    }

    @Test
    @DisplayName("자동 완료를 끄면 스케줄러를 등록하지 않고, 알림 Consumer도 꺼져 있으면 스케줄링 자체를 켜지 않는다")
    void leavesSchedulerDisabled() {
        configuredRunner
                .withPropertyValues("NOTIFICATION_STREAM_ENABLED=false", "JOB_AUTO_COMPLETION_ENABLED=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(JobAutoCompletionScheduler.class)
                        .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    private CountDownLatch givenExecutionLatch() {
        CountDownLatch executed = new CountDownLatch(1);
        when(jobService.getAutoCompletableJobIds(anyLong(), any(), anyInt())).thenAnswer(invocation -> {
            executed.countDown();
            return List.of();
        });
        return executed;
    }
}
