package com.gakkum.backend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * 같은 가입 대기 사용자의 사장님·학생 가입이 겹치는 경우는 실제로 커밋되는 두 트랜잭션이 겹쳐야 확인되므로
 * Spring이 만든 서비스를 주입하고 테스트 트랜잭션을 끈다. 공용 DB에 테스트 행을 커밋하지 않도록 DATABASE_URL이
 * 로컬 PostgreSQL일 때만 실행하며, 이 테스트가 만든 사용자 행만 지운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import(UserService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("가입 대기 사용자 동시 가입 PostgreSQL 통합 (역할 조건부 변경)")
class UserRegistrationConcurrencyIntegrationTest {

    private static final String USER_ID = "TESTREGISTRATIONRACE000001";
    private static final String USERNAME = "TEST_REGISTRATION_RACE";
    private static final String EMAIL = "registration-race@kw.ac.kr";

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("""
                insert into users (user_id, username, is_lock, role, created_at, updated_at)
                values (?, ?, false, 'PENDING', now(), now())
                """, USER_ID, USERNAME);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from users where user_id = ?", USER_ID);
    }

    @Test
    @DisplayName("사장님 가입이 사용자 행을 바꾼 채 커밋 전이면 학생 가입은 기다리고, 커밋 뒤 이미 가입한 사용자로 거부되어 역할은 사장님 하나만 남는다")
    void secondRegistrationWaitsAndIsRejectedAfterFirstCommits() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        try {
            Future<?> owner = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                User user = userService.validateOwnerRegistration(USERNAME);
                userService.completeOwnerRegistration(user, "김사장");
                firstPid.set(((Number) entityManager.createNativeQuery("select pg_backend_pid()")
                        .getSingleResult()).intValue());
                claimed.countDown();
                awaitQuietly(release);
            }));
            assertThat(claimed.await(30, TimeUnit.SECONDS)).isTrue();

            // 사장님 가입이 커밋되기 전이라 가입 대기 확인은 통과하고, 역할 변경에서 행 잠금을 기다린다
            Future<?> student = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                User user = userService.validateStudentRegistration(USERNAME, EMAIL);
                userService.completeStudentRegistration(user, "김학생", EMAIL);
            }));
            awaitSessionBlockedBy(firstPid.get(), student);

            release.countDown();
            owner.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> student.get(30, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_REGISTERED));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select role, name, email from users where user_id = ?", USER_ID);
        assertThat(row.get("role")).isEqualTo("OWNER");
        assertThat(row.get("name")).isEqualTo("김사장");
        assertThat(row.get("email")).isNull();
    }

    @Test
    @DisplayName("가입 트랜잭션 밖에서는 역할을 바꾸지 않는다")
    void requiresRegistrationTransaction() {
        User user = userService.validateOwnerRegistration(USERNAME);

        assertThatThrownBy(() -> userService.completeOwnerRegistration(user, "김사장"))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbcTemplate.queryForObject("select role from users where user_id = ?", String.class, USER_ID))
                .isEqualTo("PENDING");
    }

    /** 나중 가입이 앞선 가입의 사용자 행 잠금을 기다릴 때까지 기다린다. 기다리지 않고 끝나거나 제한 시간 안에 막히지 않으면 실패한다. */
    private void awaitSessionBlockedBy(int blockingPid, Future<?> waiter) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject("""
                    select count(*) from pg_stat_activity
                    where ? = any(pg_blocking_pids(pid)) and wait_event_type = 'Lock'
                      and query ilike '%users%'
                    """, Integer.class, blockingPid);
            if (blocked != null && blocked > 0) {
                return;
            }
            assertThat(waiter.isDone()).as("나중 가입이 사용자 행 잠금을 기다리지 않고 끝났다").isFalse();
            Thread.sleep(50);
        }
        fail("나중 가입이 사용자 행 잠금에서 대기하지 않았다");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
