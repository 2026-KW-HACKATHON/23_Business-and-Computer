package com.gakkum.backend.domain.jwt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.domain.jwt.dto.JWTResponseDTO;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.util.JWTUtil;

/**
 * refresh token 교체가 실제 PostgreSQL 에서 한 번만 성공하는지 확인한다. 두 트랜잭션이 실제로 겹쳐야 하므로 테스트 트랜잭션을 끈다.
 * 같은 초에 만든 JWT 는 글자가 같아질 수 있어, 토큰 글자는 JWTUtil 을 흉내 내어 서로 다르게 만든다.
 * 공용 DB에 테스트 행을 커밋하지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 이 테스트가 만든 행만 지운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import(JwtService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("refresh token 교체 PostgreSQL 통합 (같은 토큰 동시 사용)")
class JwtServiceConcurrencyIntegrationTest {

    private static final String USERNAME = "TESTREFRESHRACE0000000001";
    private static final String ROLE = "ROLE_STUDENT";
    private static final String OLD_REFRESH = "test-refresh-race-old";

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private JWTUtil jwtUtil;

    private final AtomicInteger issuedRefreshTokens = new AtomicInteger();

    @BeforeEach
    void setUp() {
        cleanUp();
        when(jwtUtil.isValid(anyString(), eq(false))).thenReturn(true);
        when(jwtUtil.getUsername(anyString())).thenReturn(USERNAME);
        when(jwtUtil.getRole(anyString())).thenReturn(ROLE);
        when(jwtUtil.createJWT(USERNAME, ROLE, true)).thenReturn("test-access");
        when(jwtUtil.createJWT(USERNAME, ROLE, false))
                .thenAnswer(invocation -> "test-refresh-race-new-" + issuedRefreshTokens.incrementAndGet());
        jdbcTemplate.update("insert into refresh_tokens (username, refresh, created_at) values (?, ?, now())",
                USERNAME, OLD_REFRESH);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from refresh_tokens where username = ?", USERNAME);
    }

    @Test
    @DisplayName("교체가 끝난 refreshToken을 다시 쓰면 401로 거부하고 DB에는 새 토큰 하나만 남는다")
    void rejectsReuseAfterRotation() {
        JWTResponseDTO first = jwtService.refreshToken(requestWithCookie(), new MockHttpServletResponse());
        MockHttpServletResponse reused = new MockHttpServletResponse();

        assertThat(first).isEqualTo(new JWTResponseDTO("test-access"));
        assertUnauthorized(() -> jwtService.cookie2Header(requestWithCookie(), reused));
        assertThat(reused.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(storedTokens()).containsExactly("test-refresh-race-new-1");
    }

    @Test
    @DisplayName("같은 refreshToken으로 /refresh 와 /jwt/exchange 가 동시에 오면 먼저 지운 요청만 새 토큰을 받고, 나중 요청은 기다린 뒤 401로 거부된다")
    void onlyOneConcurrentUseRotates() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        try {
            // 서비스 트랜잭션이 바깥 트랜잭션에 참여하므로 지운 행의 잠금이 release 까지 유지된다
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jwtService.refreshToken(requestWithCookie(), firstResponse);
                firstPid.set(((Number) entityManager.createNativeQuery("select pg_backend_pid()")
                        .getSingleResult()).intValue());
                deleted.countDown();
                awaitQuietly(release);
            }));
            assertThat(deleted.await(30, TimeUnit.SECONDS)).isTrue();

            Future<JWTResponseDTO> waiter = executor.submit(
                    () -> jwtService.cookie2Header(requestWithCookie(), secondResponse));
            awaitSessionBlockedBy(firstPid.get(), waiter);

            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> waiter.get(30, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        assertThat(firstResponse.getHeaders(HttpHeaders.SET_COOKIE)).singleElement().asString()
                .contains("refreshToken=test-refresh-race-new-1");
        assertThat(secondResponse.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(storedTokens()).containsExactly("test-refresh-race-new-1");
    }

    /** 나중 요청이 먼저 지운 세션의 행 잠금을 기다릴 때까지 기다린다. 기다리지 않고 끝나거나 제한 시간 안에 막히지 않으면 실패한다. */
    private void awaitSessionBlockedBy(int blockingPid, Future<?> waiter) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject("""
                    select count(*) from pg_stat_activity
                    where ? = any(pg_blocking_pids(pid)) and wait_event_type = 'Lock'
                      and query ilike '%refresh_tokens%'
                    """, Integer.class, blockingPid);
            if (blocked != null && blocked > 0) {
                return;
            }
            assertThat(waiter.isDone()).as("나중 요청이 refresh token 행 잠금을 기다리지 않고 끝났다").isFalse();
            Thread.sleep(50);
        }
        fail("나중 요청이 refresh token 행 잠금에서 대기하지 않았다");
    }

    private List<String> storedTokens() {
        return jdbcTemplate.queryForList("select refresh from refresh_tokens where username = ?", String.class,
                USERNAME);
    }

    private MockHttpServletRequest requestWithCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", OLD_REFRESH));
        return request;
    }

    private void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
