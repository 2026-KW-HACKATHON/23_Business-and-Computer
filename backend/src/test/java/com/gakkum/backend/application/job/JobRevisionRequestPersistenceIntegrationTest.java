package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.gakkum.backend.domain.job.dto.JobCommandDto.RequestJobSubmissionRevisionCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

/**
 * 수정 요청의 저장은 JobService의 쓰기 트랜잭션이 실제로 커밋되어야 확인되므로 Spring이 만든 서비스를 주입하고 테스트 트랜잭션을 끈다.
 * 공용 DB에 테스트 행을 커밋하지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 이 테스트가 만든 의뢰와 그 제출물만 지운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ JobService.class, ClockConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("사장님 수정 요청 PostgreSQL 통합 (서비스 트랜잭션 커밋·롤백·동시 요청)")
class JobRevisionRequestPersistenceIntegrationTest {

    private static final long OWNER_PROFILE_ID = 986_005L;
    private static final long STUDENT_PROFILE_ID = 986_101L;
    private static final String MESSAGE = "로고를 조금 더 크게 해주세요.";
    private static final List<String> IMAGES = List.of(
            "https://images.example.com/images/job/owner/b.png", "https://images.example.com/images/job/owner/a.png");

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

    private final List<Long> jobIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long jobId : jobIds) {
            jdbcTemplate.update("delete from job_submissions where job_id = ?", jobId);
            jdbcTemplate.update("delete from jobs where id = ?", jobId);
        }
    }

    @Test
    @DisplayName("PostgreSQL에서 수정을 요청하면 서비스 트랜잭션이 상태·요청 내용·참고 사진·요청 시각을 함께 커밋하고 새 트랜잭션에서 재조회된다")
    void commitsRevisionRequestTogether() {
        Long jobId = saveJob(2);
        Long submissionId = saveSubmission(jobId);
        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        jobService.validateRevisionRequestable(jobId, submissionId, OWNER_PROFILE_ID);
        jobService.requestRevision(command(jobId, submissionId, MESSAGE, IMAGES), OWNER_PROFILE_ID);

        JobSubmission found = findInNewTransaction(submissionId);
        assertThat(found.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.REVISION_REQUESTED);
        assertThat(found.getReviewComment()).isEqualTo(MESSAGE);
        assertThat(found.getRevisionReferenceImageUrls()).containsExactlyElementsOf(IMAGES);
        assertThat(found.getReviewedAt()).isBetween(before, LocalDateTime.now());
        assertThat(found.getMessage()).isEqualTo("제출 메시지");
        assertThat(found.getFileUrls()).containsExactly("https://example.com/b.png", "https://example.com/a.pdf");
    }

    @Test
    @DisplayName("PostgreSQL에서 수정 요청이 거부되면 상태·요청 내용·참고 사진·요청 시각 중 어느 것도 커밋되지 않는다")
    void commitsNothingWhenRejected() {
        Long jobId = saveJob(0);
        Long submissionId = saveSubmission(jobId);

        assertError(() -> jobService.requestRevision(command(jobId, submissionId, MESSAGE, IMAGES), OWNER_PROFILE_ID),
                ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);

        JobSubmission found = findInNewTransaction(submissionId);
        assertThat(found.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
        assertThat(found.getReviewComment()).isNull();
        assertThat(found.getRevisionReferenceImageUrls()).isEmpty();
        assertThat(found.getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("PostgreSQL에서 같은 제출물에 동시에 수정을 요청하면 하나만 저장되고 나머지는 의뢰 잠금 뒤 재확인에서 거부된다")
    void allowsOnlyOneConcurrentRequest() throws Exception {
        Long jobId = saveJob(2);
        Long submissionId = saveSubmission(jobId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (String message : List.of("첫 번째 요청", "두 번째 요청")) {
                Callable<String> task = () -> {
                    start.await();
                    try {
                        jobService.requestRevision(command(jobId, submissionId, message, List.of()), OWNER_PROFILE_ID);
                        return message;
                    } catch (BusinessException exception) {
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOB_SUBMISSION_ALREADY_REVIEWED);
                        return null;
                    }
                };
                futures.add(executor.submit(task));
            }
            start.countDown();

            List<String> saved = new ArrayList<>();
            for (Future<String> future : futures) {
                String message = future.get(30, TimeUnit.SECONDS);
                if (message != null) {
                    saved.add(message);
                }
            }
            assertThat(saved).hasSize(1);
            assertThat(findInNewTransaction(submissionId).getReviewComment()).isEqualTo(saved.get(0));
        } finally {
            executor.shutdownNow();
        }
    }

    private JobSubmission findInNewTransaction(Long submissionId) {
        return transactionTemplate.execute(status -> jobSubmissionRepository.findById(submissionId).orElseThrow());
    }

    private Long saveJob(int revisionCount) {
        Long jobId = jobRepository.saveAndFlush(Job.builder()
                .ownerProfileId(OWNER_PROFILE_ID)
                .title("수정 요청 통합 테스트 의뢰")
                .description("설명")
                .budget(50000L)
                .draftDeadline(LocalDate.of(2031, 2, 1))
                .finalDeadline(LocalDate.of(2031, 2, 10))
                .revisionCount(revisionCount)
                .status(JobStatus.MATCHED)
                .selectedStudentProfileId(STUDENT_PROFILE_ID)
                .build()).getId();
        jobIds.add(jobId);
        return jobId;
    }

    private Long saveSubmission(Long jobId) {
        return jobSubmissionRepository.saveAndFlush(JobSubmission.create(jobId, JobSubmissionType.DRAFT, 0,
                List.of("https://example.com/b.png", "https://example.com/a.pdf"), "제출 메시지")).getId();
    }

    private RequestJobSubmissionRevisionCommand command(
            Long jobId, Long submissionId, String message, List<String> images) {
        return RequestJobSubmissionRevisionCommand.of("KAKAO_12345", jobId, submissionId, message, images);
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
