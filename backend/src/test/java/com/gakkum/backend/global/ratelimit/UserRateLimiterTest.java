package com.gakkum.backend.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("사용자별 요청 횟수 제한기")
class UserRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
    private final UserRateLimiter limiter = new UserRateLimiter(clock, limits(3));

    @Test
    @DisplayName("최근 1시간 요청이 한도까지는 받고, 한도를 넘으면 COMMON_429로 거부한다")
    void rejectsOverLimit() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(RateLimitedAction.IMAGE_UPLOAD, "KAKAO_1");
            clock.advance(Duration.ofMinutes(1));
        }

        assertTooManyRequests(RateLimitedAction.IMAGE_UPLOAD, "KAKAO_1");
    }

    @Test
    @DisplayName("사용자와 작업마다 따로 세어, 한 사용자가 한도에 닿아도 다른 사용자·다른 작업은 받는다")
    void countsPerUserAndAction() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(RateLimitedAction.IMAGE_UPLOAD, "KAKAO_1");
        }

        assertThatCode(() -> limiter.acquire(RateLimitedAction.IMAGE_UPLOAD, "KAKAO_2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.acquire(RateLimitedAction.CHAT_ATTACHMENT_UPLOAD, "KAKAO_1"))
                .doesNotThrowAnyException();
        assertTooManyRequests(RateLimitedAction.IMAGE_UPLOAD, "KAKAO_1");
    }

    @Test
    @DisplayName("가장 오래된 요청이 1시간을 지나면 그만큼 다시 받고, 거부된 요청은 횟수에 넣지 않는다")
    void slidesWindow() {
        limiter.acquire(RateLimitedAction.STUDENT_EMAIL_SEND, "KAKAO_1");
        clock.advance(Duration.ofMinutes(30));
        limiter.acquire(RateLimitedAction.STUDENT_EMAIL_SEND, "KAKAO_1");
        limiter.acquire(RateLimitedAction.STUDENT_EMAIL_SEND, "KAKAO_1");
        assertTooManyRequests(RateLimitedAction.STUDENT_EMAIL_SEND, "KAKAO_1");

        // 첫 요청이 정확히 1시간 전이 되면 한 번 더 받는다
        clock.advance(Duration.ofMinutes(30));
        limiter.acquire(RateLimitedAction.STUDENT_EMAIL_SEND, "KAKAO_1");
        assertTooManyRequests(RateLimitedAction.STUDENT_EMAIL_SEND, "KAKAO_1");

        // 30분 전 두 요청이 지나면 두 번 더 받는다
        clock.advance(Duration.ofMinutes(30));
        limiter.acquire(RateLimitedAction.STUDENT_EMAIL_SEND, "KAKAO_1");
        limiter.acquire(RateLimitedAction.STUDENT_EMAIL_SEND, "KAKAO_1");
        assertTooManyRequests(RateLimitedAction.STUDENT_EMAIL_SEND, "KAKAO_1");
    }

    @Test
    @DisplayName("1시간 넘게 요청이 없는 사용자 기록은 다음 정리 때 지워 기록이 계속 늘지 않는다")
    void evictsIdleUsers() {
        for (int user = 0; user < 100; user++) {
            limiter.acquire(RateLimitedAction.IMAGE_UPLOAD, "KAKAO_" + user);
        }
        assertThat(limiter.trackedKeyCount()).isEqualTo(100);

        clock.advance(Duration.ofHours(1).plusMinutes(1));
        limiter.acquire(RateLimitedAction.IMAGE_UPLOAD, "KAKAO_NEW");

        assertThat(limiter.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사용자가 동시에 많이 요청해도 정확히 한도만큼만 받는다")
    void allowsExactlyLimitUnderConcurrency() throws Exception {
        UserRateLimiter concurrentLimiter = new UserRateLimiter(clock, limits(10));
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        concurrentLimiter.acquire(RateLimitedAction.OWNER_BUSINESS_VERIFICATION, "KAKAO_1");
                        return true;
                    } catch (BusinessException exception) {
                        return false;
                    }
                }));
            }
            start.countDown();

            int allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("작업마다 한도가 있어야 하고 1 이상이어야 한다")
    void requiresPositiveLimitForEveryAction() {
        Map<RateLimitedAction, Integer> missing = limits(3);
        missing.remove(RateLimitedAction.IMAGE_UPLOAD);
        Map<RateLimitedAction, Integer> zero = limits(3);
        zero.put(RateLimitedAction.IMAGE_UPLOAD, 0);

        assertThatThrownBy(() -> new UserRateLimiter(clock, missing)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserRateLimiter(clock, zero)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("설정이 없으면 이메일 발송 10회, 사업자 확인 30회, 사진·채팅 첨부·작업물 업로드 각 120회가 시간당 기본 한도다")
    void usesDefaultLimits() {
        new ApplicationContextRunner()
                .withBean(Clock.class, () -> clock)
                .withBean(UserRateLimiter.class)
                .run(context -> {
                    UserRateLimiter defaults = context.getBean(UserRateLimiter.class);
                    assertAllowedTimes(defaults, RateLimitedAction.STUDENT_EMAIL_SEND, 10);
                    assertAllowedTimes(defaults, RateLimitedAction.OWNER_BUSINESS_VERIFICATION, 30);
                    assertAllowedTimes(defaults, RateLimitedAction.IMAGE_UPLOAD, 120);
                    assertAllowedTimes(defaults, RateLimitedAction.CHAT_ATTACHMENT_UPLOAD, 120);
                    assertAllowedTimes(defaults, RateLimitedAction.JOB_SUBMISSION_FILE_UPLOAD, 120);
                });
    }

    @Test
    @DisplayName("한도는 rate-limit.*-per-hour 설정으로 바꿀 수 있다")
    void readsLimitsFromProperties() {
        new ApplicationContextRunner()
                .withBean(Clock.class, () -> clock)
                .withBean(UserRateLimiter.class)
                .withPropertyValues("rate-limit.image-upload-per-hour=2",
                        "rate-limit.student-email-send-per-hour=1")
                .run(context -> {
                    UserRateLimiter configured = context.getBean(UserRateLimiter.class);
                    assertAllowedTimes(configured, RateLimitedAction.IMAGE_UPLOAD, 2);
                    assertAllowedTimes(configured, RateLimitedAction.STUDENT_EMAIL_SEND, 1);
                    assertAllowedTimes(configured, RateLimitedAction.CHAT_ATTACHMENT_UPLOAD, 120);
                });
    }

    @Test
    @DisplayName("설정이 없으면 쓰기 작업은 일반 계정 의뢰 30 · 제안 30 · 지원 60 · 채팅 600 · 결제 준비 60회, 데모 계정 20 · 20 · 30 · 300 · 30회가 시간당 한도다")
    void usesDefaultWriteLimitsForRegularAndDemoUsers() {
        new ApplicationContextRunner()
                .withBean(Clock.class, () -> clock)
                .withBean(UserRateLimiter.class)
                .run(context -> {
                    UserRateLimiter defaults = context.getBean(UserRateLimiter.class);
                    assertAllowedTimes(defaults, RateLimitedAction.JOB_CREATE, 30, REGULAR);
                    assertAllowedTimes(defaults, RateLimitedAction.PROPOSAL_CREATE, 30, REGULAR);
                    assertAllowedTimes(defaults, RateLimitedAction.JOB_APPLICATION_CREATE, 60, REGULAR);
                    assertAllowedTimes(defaults, RateLimitedAction.CHAT_MESSAGE_SEND, 600, REGULAR);
                    assertAllowedTimes(defaults, RateLimitedAction.PAYMENT_PREPARE, 60, REGULAR);
                    assertAllowedTimes(defaults, RateLimitedAction.JOB_CREATE, 20, DEMO);
                    assertAllowedTimes(defaults, RateLimitedAction.PROPOSAL_CREATE, 20, DEMO);
                    assertAllowedTimes(defaults, RateLimitedAction.JOB_APPLICATION_CREATE, 30, DEMO);
                    assertAllowedTimes(defaults, RateLimitedAction.CHAT_MESSAGE_SEND, 300, DEMO);
                    assertAllowedTimes(defaults, RateLimitedAction.PAYMENT_PREPARE, 30, DEMO);
                    // 업로드 한도는 데모 계정도 같다
                    assertAllowedTimes(defaults, RateLimitedAction.IMAGE_UPLOAD, 120, DEMO);
                });
    }

    @Test
    @DisplayName("데모 계정 쓰기 한도는 rate-limit.demo-*-per-hour 설정으로 일반 계정과 따로 바꿀 수 있다")
    void readsDemoLimitsFromProperties() {
        new ApplicationContextRunner()
                .withBean(Clock.class, () -> clock)
                .withBean(UserRateLimiter.class)
                .withPropertyValues("rate-limit.job-create-per-hour=4", "rate-limit.demo-job-create-per-hour=2")
                .run(context -> {
                    UserRateLimiter configured = context.getBean(UserRateLimiter.class);
                    assertAllowedTimes(configured, RateLimitedAction.JOB_CREATE, 4, REGULAR);
                    assertAllowedTimes(configured, RateLimitedAction.JOB_CREATE, 2, DEMO);
                });
    }

    @Test
    @DisplayName("DEMO_ 로 시작하는 username 만 데모 계정 한도를 쓰고, 일반 계정과 따로 센다")
    void appliesDemoLimitOnlyToDemoUsernames() {
        Map<RateLimitedAction, Integer> demoLimits = limits(3);
        demoLimits.put(RateLimitedAction.JOB_CREATE, 1);
        UserRateLimiter split = new UserRateLimiter(clock, limits(3), demoLimits);

        split.acquire(RateLimitedAction.JOB_CREATE, DEMO);
        assertThatThrownBy(() -> split.acquire(RateLimitedAction.JOB_CREATE, DEMO))
                .isInstanceOf(BusinessException.class);
        for (int i = 0; i < 3; i++) {
            split.acquire(RateLimitedAction.JOB_CREATE, REGULAR);
        }
        assertThatThrownBy(() -> split.acquire(RateLimitedAction.JOB_CREATE, REGULAR))
                .isInstanceOf(BusinessException.class);

        assertThat(UserRateLimiter.isDemoUsername("KAKAO_4123456789")).isFalse();
        assertThat(UserRateLimiter.isDemoUsername("kakao_DEMO_1")).isFalse();
        assertThat(UserRateLimiter.isDemoUsername(null)).isFalse();
    }

    @Test
    @DisplayName("데모 로그인이 만드는 사장님 · 학생 계정의 username 은 데모 계정으로 알아본다")
    void recognizesUsernamesCreatedByDemoLogin() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserService userService = new UserService(userRepository, mock(JwtService.class));

        for (UserRole role : List.of(UserRole.OWNER, UserRole.STUDENT)) {
            String username = userService.createDemoUser("01K6DEMO00000000000000000A", role, "데모", null)
                    .getUsername();
            assertThat(UserRateLimiter.isDemoUsername(username)).as(username).isTrue();
        }
    }

    private static final String REGULAR = "KAKAO_1";
    private static final String DEMO = "DEMO_01K6DEMO00000000000000000A_OWNER";

    private void assertAllowedTimes(UserRateLimiter target, RateLimitedAction action, int times, String userKey) {
        for (int i = 0; i < times; i++) {
            target.acquire(action, userKey);
        }
        assertThatThrownBy(() -> target.acquire(action, userKey))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
    }

    private void assertAllowedTimes(UserRateLimiter target, RateLimitedAction action, int times) {
        for (int i = 0; i < times; i++) {
            target.acquire(action, "KAKAO_1");
        }
        assertThatThrownBy(() -> target.acquire(action, "KAKAO_1"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
    }

    private void assertTooManyRequests(RateLimitedAction action, String userKey) {
        assertThatThrownBy(() -> limiter.acquire(action, userKey))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                    assertThat(exception.getErrorCode().getCode()).isEqualTo("COMMON_429");
                });
    }

    private static Map<RateLimitedAction, Integer> limits(int limit) {
        Map<RateLimitedAction, Integer> limits = new EnumMap<>(RateLimitedAction.class);
        for (RateLimitedAction action : RateLimitedAction.values()) {
            limits.put(action, limit);
        }
        return limits;
    }

    private static final class MutableClock extends Clock {

        private volatile Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
