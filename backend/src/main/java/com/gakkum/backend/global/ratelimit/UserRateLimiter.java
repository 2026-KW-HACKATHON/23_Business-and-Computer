package com.gakkum.backend.global.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

/**
 * 사용자·작업마다 최근 1시간 요청 수를 세는 슬라이딩 윈도 제한기. 백엔드가 한 대라 메모리에만 두고, 재시작하면 처음부터 센다.
 * 전시장 방문자는 같은 와이파이 IP를 나눠 쓰므로 IP가 아니라 로그인한 사용자로만 센다.
 * 사용자·작업마다 최근 요청 시각을 최대 한도 개수만 두고, 1시간 넘게 요청이 없는 기록은 주기적으로 지워 메모리가 계속 늘지 않는다.
 */
@Component
public class UserRateLimiter {

    static final Duration WINDOW = Duration.ofHours(1);
    // 오래된 기록을 훑어 지우는 최소 간격. 요청이 올 때 이 간격이 지났으면 한 요청만 정리한다
    private static final long SWEEP_INTERVAL_MILLIS = Duration.ofMinutes(1).toMillis();

    private final Clock clock;
    private final Map<RateLimitedAction, Integer> limits;
    // 같은 키의 기록은 ConcurrentHashMap.compute 안에서만 읽고 바꾼다
    private final ConcurrentHashMap<Key, ArrayDeque<Long>> requests = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepAt;

    /** 한도는 시간당 횟수이며 설정이 없으면 기본값을 쓴다. 사진·파일 업로드는 한 번 제출에 여러 장을 올려도 넉넉하게 잡았다. */
    @Autowired
    public UserRateLimiter(
            Clock clock,
            @Value("${rate-limit.student-email-send-per-hour:10}") int studentEmailSend,
            @Value("${rate-limit.owner-business-verification-per-hour:30}") int ownerBusinessVerification,
            @Value("${rate-limit.image-upload-per-hour:120}") int imageUpload,
            @Value("${rate-limit.chat-attachment-upload-per-hour:120}") int chatAttachmentUpload,
            @Value("${rate-limit.job-submission-file-upload-per-hour:120}") int jobSubmissionFileUpload) {
        this(clock, Map.of(
                RateLimitedAction.STUDENT_EMAIL_SEND, studentEmailSend,
                RateLimitedAction.OWNER_BUSINESS_VERIFICATION, ownerBusinessVerification,
                RateLimitedAction.IMAGE_UPLOAD, imageUpload,
                RateLimitedAction.CHAT_ATTACHMENT_UPLOAD, chatAttachmentUpload,
                RateLimitedAction.JOB_SUBMISSION_FILE_UPLOAD, jobSubmissionFileUpload));
    }

    UserRateLimiter(Clock clock, Map<RateLimitedAction, Integer> limits) {
        this.clock = clock;
        this.limits = new EnumMap<>(limits);
        for (RateLimitedAction action : RateLimitedAction.values()) {
            Integer limit = this.limits.get(action);
            if (limit == null || limit < 1) {
                throw new IllegalArgumentException("Rate limit must be positive: " + action);
            }
        }
        this.lastSweepAt = new AtomicLong(clock.millis());
    }

    /**
     * 이 사용자의 이 작업 요청을 한 번 센다. 최근 1시간 요청이 한도에 이미 닿았으면 세지 않고 COMMON_429로 거부한다.
     * 거부된 요청은 횟수에 넣지 않으므로, 가장 오래된 요청이 1시간을 지나면 다시 받을 수 있다.
     */
    public void acquire(RateLimitedAction action, String userKey) {
        long now = clock.millis();
        sweepIfDue(now);

        int limit = limits.get(action);
        boolean[] allowed = { false };
        requests.compute(new Key(action, userKey), (key, timestamps) -> {
            ArrayDeque<Long> recent = timestamps == null ? new ArrayDeque<>() : timestamps;
            dropExpired(recent, now);
            if (recent.size() < limit) {
                recent.addLast(now);
                allowed[0] = true;
            }
            return recent.isEmpty() ? null : recent;
        });
        if (!allowed[0]) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }

    /** 기록을 들고 있는 사용자·작업 수. 오래된 기록이 지워지는지 확인하는 테스트용이다. */
    int trackedKeyCount() {
        return requests.size();
    }

    private void sweepIfDue(long now) {
        long last = lastSweepAt.get();
        if (now - last < SWEEP_INTERVAL_MILLIS || !lastSweepAt.compareAndSet(last, now)) {
            return;
        }
        for (Key key : requests.keySet()) {
            requests.computeIfPresent(key, (ignored, recent) -> {
                dropExpired(recent, now);
                return recent.isEmpty() ? null : recent;
            });
        }
    }

    private static void dropExpired(ArrayDeque<Long> recent, long now) {
        long windowStart = now - WINDOW.toMillis();
        while (!recent.isEmpty() && recent.peekFirst() <= windowStart) {
            recent.pollFirst();
        }
    }

    private record Key(RateLimitedAction action, String userKey) {
    }
}
