package com.gakkum.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.config.ClockConfig;
import com.gakkum.backend.domain.auth.client.NtsBusinessVerificationClient;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;

/**
 * 인증 행 잠금은 실제로 커밋되는 두 트랜잭션이 겹쳐야 확인되므로 Spring이 만든 서비스를 주입하고 테스트 트랜잭션을 끈다.
 * 공용 DB에 테스트 행을 커밋하지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 이 테스트가 만든 인증 행만 지운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ AuthService.class, ClockConfig.class })
@TestPropertySource(properties = "spring.mail.username=sender@example.com")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("학생 이메일 인증 PostgreSQL 통합 (인증 행 잠금)")
class AuthServiceConcurrencyIntegrationTest {

    private static final String VERIFY_USER_ID = "TESTEMAILLOCKVERIFY0000001";
    private static final String SEND_USER_ID = "TESTEMAILLOCKSEND000000001";
    private static final String EMAIL = "lock-test@kw.ac.kr";
    private static final String CODE = "123456";
    private static final String WRONG_CODE = "000000";

    @Autowired
    private AuthService authService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private NtsBusinessVerificationClient businessVerificationClient;

    @BeforeEach
    void setUp() {
        cleanUp();
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from student_email_verifications where user_id in (?, ?)",
                VERIFY_USER_ID, SEND_USER_ID);
    }

    @Test
    @DisplayName("오입력 4회 상태에서 다섯 번째 오입력이 행을 잠근 동안 맞는 번호 확인은 대기하고, 커밋 뒤 5회 제한으로 거부된다")
    void waitingVerificationSeesFifthFailureAfterCommit() throws Exception {
        Instant now = Instant.now();
        insertVerification(VERIFY_USER_ID, now, now.plusSeconds(600), 4);

        assertSecondRejectedAfterFirstCommitted(
                () -> {
                    try {
                        authService.verifyStudentEmail(VERIFY_USER_ID, EMAIL, WRONG_CODE);
                    } catch (BusinessException ignored) {
                        // 오입력 예외는 롤백하지 않으므로 횟수 증가가 바깥 트랜잭션과 함께 커밋된다
                    }
                },
                () -> authService.verifyStudentEmail(VERIFY_USER_ID, EMAIL, CODE),
                ErrorCode.STUDENT_EMAIL_VERIFICATION_INVALID);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select failed_attempts, verified_at from student_email_verifications where user_id = ?",
                VERIFY_USER_ID);
        assertThat(row.get("failed_attempts")).isEqualTo(5);
        assertThat(row.get("verified_at")).isNull();
    }

    @Test
    @DisplayName("재발송이 행을 잠근 동안 다른 재발송은 대기하고, 커밋 뒤 60초 대기 시간으로 거부되어 메일은 한 번만 나간다")
    void waitingResendSeesCooldownAfterCommit() throws Exception {
        Instant sentAt = Instant.now().minusSeconds(120);
        insertVerification(SEND_USER_ID, sentAt, sentAt.plusSeconds(600), 0);

        assertSecondRejectedAfterFirstCommitted(
                () -> authService.sendStudentEmailVerification(SEND_USER_ID, EMAIL),
                () -> authService.sendStudentEmailVerification(SEND_USER_ID, EMAIL),
                ErrorCode.STUDENT_EMAIL_VERIFICATION_COOLDOWN);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    /**
     * 먼저 실행한 호출이 인증 행 잠금을 쥔 채 커밋하지 않는 동안 나중 호출이 그 잠금에서 기다리고, 커밋 후 바뀐 상태를 보고 거부되는지 확인한다.
     * 나중 호출이 늦게 시작한 것과 구분하도록, DB가 그 세션을 먼저 실행한 세션에 막힌 것으로 보고할 때까지 기다린 뒤 잠금을 푼다.
     */
    private void assertSecondRejectedAfterFirstCommitted(Runnable first, Runnable second, ErrorCode expected)
            throws Exception {
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

    /** 인증 행 잠금을 기다리는 세션이 생길 때까지 기다린다. 나중 호출이 기다리지 않고 끝나거나 제한 시간 안에 막히지 않으면 실패한다. */
    private void awaitSessionBlockedBy(int blockingPid, Future<?> waiter) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject("""
                    select count(*) from pg_stat_activity
                    where ? = any(pg_blocking_pids(pid)) and wait_event_type = 'Lock'
                      and query ilike '%student_email_verifications%'
                    """, Integer.class, blockingPid);
            if (blocked != null && blocked > 0) {
                return;
            }
            assertThat(waiter.isDone()).as("나중 호출이 인증 행 잠금을 기다리지 않고 끝났다").isFalse();
            Thread.sleep(50);
        }
        fail("나중 호출이 인증 행 잠금에서 대기하지 않았다");
    }

    private void insertVerification(String userId, Instant sentAt, Instant codeExpiresAt, int failedAttempts) {
        jdbcTemplate.update("""
                insert into student_email_verifications
                    (user_id, email, code_hash, sent_at, code_expires_at, verified_at, failed_attempts)
                values (?, ?, ?, ?, ?, null, ?)
                """, userId, EMAIL, new BCryptPasswordEncoder().encode(CODE),
                Timestamp.from(sentAt), Timestamp.from(codeExpiresAt), failedAttempts);
        assertThat(jdbcTemplate.queryForList(
                "select user_id from student_email_verifications where user_id = ?", String.class, userId))
                .isEqualTo(List.of(userId));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
