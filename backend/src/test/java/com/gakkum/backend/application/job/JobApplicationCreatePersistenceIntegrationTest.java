package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Example;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.config.ClockConfig;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobApplicationCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * 의뢰 행 잠금과 유니크 제약은 서로 다른 트랜잭션이 실제로 커밋되어야 확인되므로 테스트 트랜잭션을 끄고 직접 데이터를 정리한다.
 * 공용 DB에 테스트 행을 커밋하지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 이 테스트가 만든 의뢰와 그 지원서만 지운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ JobService.class, ClockConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("학생 의뢰 지원 PostgreSQL 통합 (저장·중복·동시성·선정/취소와의 순서)")
class JobApplicationCreatePersistenceIntegrationTest {

    private static final long OWNER_PROFILE_ID = 985_005L;
    private static final long STUDENT_PROFILE_ID = 985_101L;
    private static final long OTHER_STUDENT_PROFILE_ID = 985_102L;
    private static final String SUMMARY = "매장 분위기에 맞는 메뉴판을 제작하겠습니다.";
    private static final String WORK_PLAN = "요구사항 확인 후 시안을 제작하고\n피드백을 반영하겠습니다.";
    private static final String DELIVERY_METHOD = "인쇄용 PDF와 편집 가능한 원본 파일로 전달하겠습니다.";

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

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
            jdbcTemplate.update("delete from job_applications where job_id = ?", jobId);
            jdbcTemplate.update("delete from jobs where id = ?", jobId);
        }
    }

    @Test
    @DisplayName("PostgreSQL에서 모집 중 의뢰에 지원하면 학생·의뢰·입력 내용이 PENDING으로 저장되고 재조회된다")
    void persistsPendingApplication() {
        Job job = saveOpenJob();

        JobApplication created = jobService.createJobApplication(command(job.getId()), STUDENT_PROFILE_ID);

        JobApplication found = jobApplicationRepository.findById(created.getId()).orElseThrow();
        assertThat(found.getStudentProfileId()).isEqualTo(STUDENT_PROFILE_ID);
        assertThat(found.getJobId()).isEqualTo(job.getId());
        assertThat(found.getSummary()).isEqualTo(SUMMARY);
        assertThat(found.getWorkPlan()).isEqualTo(WORK_PLAN);
        assertThat(found.getDeliveryMethod()).isEqualTo(DELIVERY_METHOD);
        assertThat(found.getStatus()).isEqualTo(JobApplicationStatus.PENDING);
        assertThat(found.getCreatedAt()).isNotNull();
        Job foundJob = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(foundJob.getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(foundJob.getSelectedStudentProfileId()).isNull();
    }

    @Test
    @DisplayName("PostgreSQL에서 작업 마감일이 지난 모집 중 의뢰에도 지원할 수 있다")
    void allowsOpenJobPastDeadline() {
        Job job = saveJob(LocalDate.now().minusDays(5), LocalDate.now().minusDays(1));

        JobApplication created = jobService.createJobApplication(command(job.getId()), STUDENT_PROFILE_ID);

        assertThat(jobApplicationRepository.findById(created.getId())).isPresent();
    }

    @ParameterizedTest
    @EnumSource(JobApplicationStatus.class)
    @DisplayName("PostgreSQL에서 기존 지원서가 어떤 상태든 같은 의뢰에 다시 지원하면 중복으로 거부하고 기존 지원서만 남는다")
    void rejectsReapplicationRegardlessOfStatus(JobApplicationStatus existingStatus) {
        Job job = saveOpenJob();
        Long existingId = jobApplicationRepository.saveAndFlush(JobApplication.builder()
                .jobId(job.getId()).studentProfileId(STUDENT_PROFILE_ID)
                .summary("기존 요약").workPlan("기존 계획").deliveryMethod("기존 전달")
                .status(existingStatus).build()).getId();

        assertThatThrownBy(() -> jobService.createJobApplication(command(job.getId()), STUDENT_PROFILE_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOB_APPLICATION_ALREADY_EXISTS));

        List<JobApplication> applications = applicationsOf(job.getId());
        assertThat(applications).extracting(JobApplication::getId).containsExactly(existingId);
        assertThat(applications.get(0).getSummary()).isEqualTo("기존 요약");
        assertThat(applications.get(0).getStatus()).isEqualTo(existingStatus);
    }

    @Test
    @DisplayName("PostgreSQL에서 다른 학생은 이미 지원자가 있는 같은 의뢰에 지원할 수 있다")
    void allowsOtherStudentOnSameJob() {
        Job job = saveOpenJob();
        jobService.createJobApplication(command(job.getId()), STUDENT_PROFILE_ID);

        jobService.createJobApplication(command(job.getId()), OTHER_STUDENT_PROFILE_ID);

        assertThat(applicationsOf(job.getId())).extracting(JobApplication::getStudentProfileId)
                .containsExactlyInAnyOrder(STUDENT_PROFILE_ID, OTHER_STUDENT_PROFILE_ID);
    }

    @Test
    @DisplayName("PostgreSQL에서 같은 학생이 같은 의뢰에 동시에 지원하면 한 건만 저장되고 나머지는 중복으로 거부된다")
    void savesOnlyOneOfConcurrentDuplicates() throws Exception {
        Job job = saveOpenJob();
        int requests = 3;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<JobApplication>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return jobService.createJobApplication(command(job.getId()), STUDENT_PROFILE_ID);
                }));
            }
            start.countDown();

            int succeeded = 0;
            for (Future<JobApplication> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOfSatisfying(BusinessException.class, cause ->
                            assertThat(cause.getErrorCode()).isEqualTo(ErrorCode.JOB_APPLICATION_ALREADY_EXISTS));
                }
            }
            assertThat(succeeded).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(applicationsOf(job.getId())).hasSize(1);
    }

    @Test
    @DisplayName("PostgreSQL에서 다른 학생 선정이 먼저 확정되면 대기하던 지원은 409 상태 오류로 거부되고 저장되지 않는다")
    void rejectsApplicationAfterSelectionCommitted() throws Exception {
        Job job = saveOpenJob();

        assertRejectedAfterCommittedChange(job.getId(), locked -> locked.match(OTHER_STUDENT_PROFILE_ID));

        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.MATCHED);
    }

    @Test
    @DisplayName("PostgreSQL에서 의뢰 취소가 먼저 확정되면 대기하던 지원은 409 상태 오류로 거부되고 저장되지 않는다")
    void rejectsApplicationAfterCancellationCommitted() throws Exception {
        Job job = saveOpenJob();

        assertRejectedAfterCommittedChange(job.getId(),
                locked -> locked.cancel(LocalDateTime.now(), "일정 변경", "죄송합니다."));

        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    /**
     * 의뢰 행을 잠근 트랜잭션이 상태를 바꾸는 동안 지원이 그 잠금에서 기다리고, 커밋 후 바뀐 상태를 보고 거부되는지 확인한다.
     * 지원이 늦게 시작한 것과 구분하도록, DB가 지원 세션을 상태 변경 세션에 막힌 것으로 보고할 때까지 기다린 뒤 잠금을 푼다.
     */
    private void assertRejectedAfterCommittedChange(Long jobId, Consumer<Job> change) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger changerPid = new AtomicInteger();
        try {
            Future<?> changer = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                change.accept(jobRepository.findLockedById(jobId).orElseThrow());
                changerPid.set(((Number) entityManager.createNativeQuery("select pg_backend_pid()")
                        .getSingleResult()).intValue());
                locked.countDown();
                awaitQuietly(release);
            }));
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();

            Callable<JobApplication> apply =
                    () -> jobService.createJobApplication(command(jobId), STUDENT_PROFILE_ID);
            Future<JobApplication> applicant = executor.submit(apply);
            awaitSessionBlockedBy(changerPid.get(), applicant);
            assertThat(applicant.isDone()).isFalse();

            release.countDown();
            changer.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> applicant.get(30, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOB_APPLICATION_NOT_AVAILABLE));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        assertThat(applicationsOf(jobId)).isEmpty();
    }

    /** 의뢰 행 잠금을 기다리는 세션이 생길 때까지 기다린다. 지원이 기다리지 않고 끝나거나 제한 시간 안에 막히지 않으면 실패한다. */
    private void awaitSessionBlockedBy(int blockingPid, Future<?> applicant) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject("""
                    select count(*) from pg_stat_activity
                    where ? = any(pg_blocking_pids(pid)) and wait_event_type = 'Lock' and query ilike '%jobs%'
                    """, Integer.class, blockingPid);
            if (blocked != null && blocked > 0) {
                return;
            }
            assertThat(applicant.isDone()).as("지원이 의뢰 행 잠금을 기다리지 않고 끝났다").isFalse();
            Thread.sleep(50);
        }
        fail("지원 쿼리가 의뢰 행 잠금에서 대기하지 않았다");
    }

    private List<JobApplication> applicationsOf(Long jobId) {
        return jobApplicationRepository.findAll(Example.of(JobApplication.builder().jobId(jobId).build()));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private CreateJobApplicationCommand command(Long jobId) {
        return CreateJobApplicationCommand.of("unused", jobId, SUMMARY, WORK_PLAN, DELIVERY_METHOD);
    }

    private Job saveOpenJob() {
        return saveJob(LocalDate.now().plusDays(3), LocalDate.now().plusDays(7));
    }

    private Job saveJob(LocalDate draftDeadline, LocalDate finalDeadline) {
        Job saved = jobRepository.saveAndFlush(Job.create(
                OWNER_PROFILE_ID, "지원 테스트 의뢰", "설명", 100_000L, draftDeadline, finalDeadline, 2));
        jobIds.add(saved.getId());
        return saved;
    }
}
