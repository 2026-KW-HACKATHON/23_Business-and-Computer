package com.gakkum.backend.application.job.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.job.service.JobService;

@DisplayName("미확인 제출물 자동 완료 스케줄러 (기준 시각·100건 단위 이어 읽기·개별 실패 격리)")
class JobAutoCompletionSchedulerTest {

    private static final LocalDateTime REFERENCE_TIME = LocalDateTime.of(2026, 9, 28, 3, 15, 30);

    private final JobService jobService = mock(JobService.class);
    private final JobFacade jobFacade = mock(JobFacade.class);
    private final Clock clock = mock(Clock.class);
    private final JobAutoCompletionScheduler scheduler = new JobAutoCompletionScheduler(jobService, jobFacade, clock);

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-28T03:15:30Z"));
    }

    @Test
    @DisplayName("대상이 100건을 넘으면 마지막 ID 뒤를 이어 읽어 모두 처리하고, 기준 시각은 실행마다 한 번만 계산한다")
    void processesMoreThanOneBatch() {
        when(jobService.getAutoCompletableJobIds(0L, REFERENCE_TIME, 100)).thenReturn(ids(1, 100));
        when(jobService.getAutoCompletableJobIds(100L, REFERENCE_TIME, 100)).thenReturn(ids(101, 200));
        when(jobService.getAutoCompletableJobIds(200L, REFERENCE_TIME, 100)).thenReturn(ids(201, 250));

        scheduler.completeExpiredSubmissions();

        for (Long jobId : ids(1, 250)) {
            verify(jobFacade).autoCompleteSubmission(jobId, REFERENCE_TIME);
        }
        verify(jobService, times(3)).getAutoCompletableJobIds(anyLong(), eq(REFERENCE_TIME), anyInt());
        verify(clock, times(1)).instant();
    }

    @Test
    @DisplayName("대상이 정확히 100건이면 다음 구간이 비어 있는 것을 확인하고 끝낸다")
    void stopsAfterEmptyBatch() {
        when(jobService.getAutoCompletableJobIds(0L, REFERENCE_TIME, 100)).thenReturn(ids(1, 100));
        when(jobService.getAutoCompletableJobIds(100L, REFERENCE_TIME, 100)).thenReturn(List.of());

        scheduler.completeExpiredSubmissions();

        verify(jobFacade, times(100)).autoCompleteSubmission(anyLong(), eq(REFERENCE_TIME));
        verify(jobService, times(2)).getAutoCompletableJobIds(anyLong(), eq(REFERENCE_TIME), anyInt());
    }

    @Test
    @DisplayName("한 건이 실패해도 예외를 밖으로 던지지 않고 다음 건을 계속 처리한다")
    void continuesAfterIndividualFailure() {
        when(jobService.getAutoCompletableJobIds(0L, REFERENCE_TIME, 100)).thenReturn(List.of(11L, 12L, 13L));
        when(jobFacade.autoCompleteSubmission(11L, REFERENCE_TIME)).thenReturn(true);
        when(jobFacade.autoCompleteSubmission(12L, REFERENCE_TIME)).thenThrow(new IllegalStateException("테스트 실패"));
        when(jobFacade.autoCompleteSubmission(13L, REFERENCE_TIME)).thenReturn(true);

        assertThatCode(scheduler::completeExpiredSubmissions).doesNotThrowAnyException();

        verify(jobFacade).autoCompleteSubmission(11L, REFERENCE_TIME);
        verify(jobFacade).autoCompleteSubmission(12L, REFERENCE_TIME);
        verify(jobFacade).autoCompleteSubmission(13L, REFERENCE_TIME);
        verifyNoMoreInteractions(jobFacade);
    }

    @Test
    @DisplayName("대상 조회가 실패하면 예외를 밖으로 던지지 않고 이번 실행을 끝낸다")
    void endsRunWhenQueryFails() {
        when(jobService.getAutoCompletableJobIds(0L, REFERENCE_TIME, 100))
                .thenThrow(new IllegalStateException("테스트 실패"));

        assertThatCode(scheduler::completeExpiredSubmissions).doesNotThrowAnyException();

        verifyNoInteractions(jobFacade);
    }

    private List<Long> ids(long from, long to) {
        return LongStream.rangeClosed(from, to).boxed().toList();
    }
}
