package com.gakkum.backend.application.job.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;

import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.job.service.JobService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 최신 제출물이 검토 없이 자동 완료 기간을 넘긴 진행 중 의뢰를 주기적으로 완료한다. */
@RequiredArgsConstructor
@Slf4j
public class JobAutoCompletionScheduler {

    private static final int BATCH_SIZE = 100;

    private final JobService jobService;
    private final JobFacade jobFacade;
    private final Clock clock;

    /**
     * 기준 시각은 실행마다 한 번만 계산한다. 대상을 ID 오름차순으로 나눠 읽고 마지막 ID 뒤를 이어 처리하므로
     * 실패하거나 건너뛴 의뢰를 같은 실행에서 다시 읽지 않는다. 실패한 의뢰는 다음 실행에서 다시 대상이 된다.
     */
    @Scheduled(fixedDelayString = "${job.auto-completion.interval:1h}")
    public void completeExpiredSubmissions() {
        LocalDateTime referenceTime = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        long afterJobId = 0L;
        while (true) {
            List<Long> jobIds;
            try {
                jobIds = jobService.getAutoCompletableJobIds(afterJobId, referenceTime, BATCH_SIZE);
            } catch (RuntimeException exception) {
                log.error("자동 완료 대상 조회 실패: afterJobId={}", afterJobId, exception);
                return;
            }
            for (Long jobId : jobIds) {
                complete(jobId, referenceTime);
            }
            if (jobIds.size() < BATCH_SIZE) {
                return;
            }
            afterJobId = jobIds.get(jobIds.size() - 1);
        }
    }

    private void complete(Long jobId, LocalDateTime referenceTime) {
        try {
            if (jobFacade.autoCompleteSubmission(jobId, referenceTime)) {
                log.info("미확인 제출물 자동 완료: jobId={}", jobId);
            }
        } catch (RuntimeException exception) {
            log.error("미확인 제출물 자동 완료 실패: jobId={}", jobId, exception);
        }
    }
}
