package com.gakkum.backend.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.application.job.scheduler.JobAutoCompletionScheduler;
import com.gakkum.backend.domain.job.service.JobService;

/** 알림 Consumer가 꺼진 환경에서도 자동 완료가 돌도록 스케줄링을 이 설정에서 따로 활성화한다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "job.auto-completion", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class JobAutoCompletionConfig {

    @Bean
    JobAutoCompletionScheduler jobAutoCompletionScheduler(JobService jobService, JobFacade jobFacade, Clock clock) {
        return new JobAutoCompletionScheduler(jobService, jobFacade, clock);
    }
}
