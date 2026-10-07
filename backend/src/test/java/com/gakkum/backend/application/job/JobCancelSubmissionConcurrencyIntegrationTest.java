package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.config.ClockConfig;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CancelJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * 취소와 초안 제출의 순서는 JobService의 쓰기 트랜잭션이 실제로 커밋되어야 확인되므로 Spring이 만든 서비스를 주입하고 테스트 트랜잭션을 끈다.
 * 공용 DB에 테스트 행을 커밋하지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 이 테스트가 만든 의뢰와 그 제출물만 지운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ JobService.class, ClockConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("의뢰 취소와 초안 제출 PostgreSQL 통합 (의뢰 행 잠금 순서)")
class JobCancelSubmissionConcurrencyIntegrationTest {

    private static final long OWNER_PROFILE_ID = 987_005L;
    private static final long STUDENT_PROFILE_ID = 987_101L;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobSubmissionRepository jobSubmissionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private final List<Long> jobIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long jobId : jobIds) {
            jdbcTemplate.update("delete from job_submissions where job_id = ?", jobId);
            jdbcTemplate.update("delete from jobs where id = ?", jobId);
        }
    }

    @Test
    @DisplayName("PostgreSQL에서 초안 제출이 의뢰 잠금을 쥔 동안 취소는 대기하고, 제출 커밋 뒤 JOB_409_CANCEL_SUBMITTED로 거부된다")
    void rejectsWaitingCancelAfterDraftCommitted() throws Exception {
        Long jobId = saveMatchedJob();

        assertRejectedAfterCommitted(
                () -> jobService.submitDraft(submitCommand(jobId), STUDENT_PROFILE_ID, Map.of()),
                () -> jobService.cancelJob(cancelCommand(jobId), OWNER_PROFILE_ID),
                ErrorCode.JOB_CANCEL_SUBMITTED);

        assertThat(findStatus(jobId)).isEqualTo(JobStatus.MATCHED);
        assertThat(jobSubmissionRepository.existsByJobId(jobId)).isTrue();
    }

    @Test
    @DisplayName("PostgreSQL에서 취소가 의뢰 잠금을 쥔 동안 초안 제출은 대기하고, 취소 커밋 뒤 JOB_SUBMISSION_409_STATUS로 거부된다")
    void rejectsWaitingDraftAfterCancelCommitted() throws Exception {
        Long jobId = saveMatchedJob();

        assertRejectedAfterCommitted(
                () -> jobService.cancelJob(cancelCommand(jobId), OWNER_PROFILE_ID),
                () -> jobService.submitDraft(submitCommand(jobId), STUDENT_PROFILE_ID, Map.of()),
                ErrorCode.JOB_SUBMISSION_NOT_AVAILABLE);

        assertThat(findStatus(jobId)).isEqualTo(JobStatus.CANCELLED);
        assertThat(jobSubmissionRepository.existsByJobId(jobId)).isFalse();
    }

    /**
     * 먼저 실행한 서비스 호출이 의뢰 행 잠금을 쥔 채 커밋하지 않는 동안 나중 호출이 그 잠금에서 기다리고, 커밋 후 바뀐 상태를 보고 거부되는지 확인한다.
     * 나중 호출이 늦게 시작한 것과 구분하도록, DB가 그 세션을 먼저 실행한 세션에 막힌 것으로 보고할 때까지 기다린 뒤 잠금을 푼다.
     */
    private void assertRejectedAfterCommitted(Runnable first, Runnable second, ErrorCode expected) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        try {
            // 서비스 트랜잭션이 바깥 트랜잭션에 참여하므로 잠금이 release까지 유지된다
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                first.run();
                firstPid.set(((Number) entityManager.createNativeQuery("select pg_backend_pid()")
                        .getSingleResult()).intValue());
                locked.countDown();
                awaitQuietly(release);
            }));
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();

            Future<?> waiter = executor.submit(second);
            awaitSessionBlockedBy(firstPid.get(), waiter);
            assertThat(waiter.isDone()).isFalse();

            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> waiter.get(30, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(expected));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /** 의뢰 행 잠금을 기다리는 세션이 생길 때까지 기다린다. 나중 호출이 기다리지 않고 끝나거나 제한 시간 안에 막히지 않으면 실패한다. */
    private void awaitSessionBlockedBy(int blockingPid, Future<?> waiter) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject("""
                    select count(*) from pg_stat_activity
                    where ? = any(pg_blocking_pids(pid)) and wait_event_type = 'Lock' and query ilike '%jobs%'
                    """, Integer.class, blockingPid);
            if (blocked != null && blocked > 0) {
                return;
            }
            assertThat(waiter.isDone()).as("나중 호출이 의뢰 행 잠금을 기다리지 않고 끝났다").isFalse();
            Thread.sleep(50);
        }
        fail("나중 호출이 의뢰 행 잠금에서 대기하지 않았다");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private JobStatus findStatus(Long jobId) {
        return transactionTemplate.execute(status -> jobRepository.findById(jobId).orElseThrow().getStatus());
    }

    private Long saveMatchedJob() {
        Long jobId = jobRepository.saveAndFlush(Job.builder()
                .ownerProfileId(OWNER_PROFILE_ID)
                .title("취소·제출 동시성 통합 테스트 의뢰")
                .description("설명")
                .budget(50000L)
                .draftDeadline(LocalDate.of(2031, 2, 1))
                .finalDeadline(LocalDate.of(2031, 2, 10))
                .revisionCount(2)
                .status(JobStatus.MATCHED)
                .selectedStudentProfileId(STUDENT_PROFILE_ID)
                .build()).getId();
        jobIds.add(jobId);
        return jobId;
    }

    private CancelJobCommand cancelCommand(Long jobId) {
        return CancelJobCommand.of("KAKAO_12345", jobId, "매장 일정이 변경되었습니다.", "진행해 주셔서 감사합니다.");
    }

    private CreateJobSubmissionCommand submitCommand(Long jobId) {
        return CreateJobSubmissionCommand.of(
                "KAKAO_67890", jobId, List.of("https://example.com/draft.png"), "초안입니다.");
    }
}
