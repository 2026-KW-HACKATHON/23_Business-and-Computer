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
 * 데모 계정은 로그인 없이 누구나 만들 수 있어 쓰기 작업의 한도를 더 낮게 둔다. 데모 계정은 username 으로 DB 조회 없이 알아본다.
 */
@Component
public class UserRateLimiter {

    static final Duration WINDOW = Duration.ofHours(1);
    // 오래된 기록을 훑어 지우는 최소 간격. 요청이 올 때 이 간격이 지났으면 한 요청만 정리한다
    private static final long SWEEP_INTERVAL_MILLIS = Duration.ofMinutes(1).toMillis();
    // 데모 로그인이 만드는 계정의 username 앞부분 (UserService.createDemoUser). 소셜 로그인 계정은 KAKAO_ 로 시작한다
    static final String DEMO_USERNAME_PREFIX = "DEMO_";

    private final Clock clock;
    private final Map<RateLimitedAction, Integer> limits;
    private final Map<RateLimitedAction, Integer> demoLimits;
    // 같은 키의 기록은 ConcurrentHashMap.compute 안에서만 읽고 바꾼다
    private final ConcurrentHashMap<Key, ArrayDeque<Long>> requests = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepAt;

    /**
     * 한도는 시간당 횟수이며 설정이 없으면 기본값을 쓴다. 사진·파일 업로드는 한 번 제출에 여러 장을 올려도 넉넉하게 잡았다.
     * 쓰기 작업은 보통 쓰임의 몇 배로 잡아 정상 사용을 막지 않고, 데모 계정은 전시장에서 한 쌍을 여러 방문자가 이어 써도 되게 잡았다.
     * 업로드 · 메일 · 사업자 확인은 데모 계정도 같은 한도다.
     */
    @Autowired
    public UserRateLimiter(
            Clock clock,
            @Value("${rate-limit.student-email-send-per-hour:10}") int studentEmailSend,
            @Value("${rate-limit.owner-business-verification-per-hour:30}") int ownerBusinessVerification,
            @Value("${rate-limit.image-upload-per-hour:120}") int imageUpload,
            @Value("${rate-limit.chat-attachment-upload-per-hour:120}") int chatAttachmentUpload,
            @Value("${rate-limit.job-submission-file-upload-per-hour:120}") int jobSubmissionFileUpload,
            @Value("${rate-limit.job-create-per-hour:30}") int jobCreate,
            @Value("${rate-limit.proposal-create-per-hour:30}") int proposalCreate,
            @Value("${rate-limit.job-application-create-per-hour:60}") int jobApplicationCreate,
            @Value("${rate-limit.chat-message-send-per-hour:600}") int chatMessageSend,
            @Value("${rate-limit.payment-prepare-per-hour:60}") int paymentPrepare,
            @Value("${rate-limit.demo-job-create-per-hour:20}") int demoJobCreate,
            @Value("${rate-limit.demo-proposal-create-per-hour:20}") int demoProposalCreate,
            @Value("${rate-limit.demo-job-application-create-per-hour:30}") int demoJobApplicationCreate,
            @Value("${rate-limit.demo-chat-message-send-per-hour:300}") int demoChatMessageSend,
            @Value("${rate-limit.demo-payment-prepare-per-hour:30}") int demoPaymentPrepare) {
        this(clock,
                limits(studentEmailSend, ownerBusinessVerification, imageUpload, chatAttachmentUpload,
                        jobSubmissionFileUpload, jobCreate, proposalCreate, jobApplicationCreate, chatMessageSend,
                        paymentPrepare),
                limits(studentEmailSend, ownerBusinessVerification, imageUpload, chatAttachmentUpload,
                        jobSubmissionFileUpload, demoJobCreate, demoProposalCreate, demoJobApplicationCreate,
                        demoChatMessageSend, demoPaymentPrepare));
    }

    /** 데모 계정도 같은 한도를 쓴다. */
    UserRateLimiter(Clock clock, Map<RateLimitedAction, Integer> limits) {
        this(clock, limits, limits);
    }

    UserRateLimiter(Clock clock, Map<RateLimitedAction, Integer> limits, Map<RateLimitedAction, Integer> demoLimits) {
        this.clock = clock;
        this.limits = checkedLimits(limits);
        this.demoLimits = checkedLimits(demoLimits);
        this.lastSweepAt = new AtomicLong(clock.millis());
    }

    /**
     * 이 사용자의 이 작업 요청을 한 번 센다. 최근 1시간 요청이 한도에 이미 닿았으면 세지 않고 COMMON_429로 거부한다.
     * 거부된 요청은 횟수에 넣지 않으므로, 가장 오래된 요청이 1시간을 지나면 다시 받을 수 있다.
     */
    public void acquire(RateLimitedAction action, String userKey) {
        long now = clock.millis();
        sweepIfDue(now);

        int limit = (isDemoUsername(userKey) ? demoLimits : limits).get(action);
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

    /** 데모 로그인이 만든 계정인지. username 은 서버가 정해 JWT 에 서명하므로 사용자가 바꿀 수 없다. */
    static boolean isDemoUsername(String username) {
        return username != null && username.startsWith(DEMO_USERNAME_PREFIX);
    }

    private static Map<RateLimitedAction, Integer> limits(int studentEmailSend, int ownerBusinessVerification,
            int imageUpload, int chatAttachmentUpload, int jobSubmissionFileUpload, int jobCreate, int proposalCreate,
            int jobApplicationCreate, int chatMessageSend, int paymentPrepare) {
        Map<RateLimitedAction, Integer> limits = new EnumMap<>(RateLimitedAction.class);
        limits.put(RateLimitedAction.STUDENT_EMAIL_SEND, studentEmailSend);
        limits.put(RateLimitedAction.OWNER_BUSINESS_VERIFICATION, ownerBusinessVerification);
        limits.put(RateLimitedAction.IMAGE_UPLOAD, imageUpload);
        limits.put(RateLimitedAction.CHAT_ATTACHMENT_UPLOAD, chatAttachmentUpload);
        limits.put(RateLimitedAction.JOB_SUBMISSION_FILE_UPLOAD, jobSubmissionFileUpload);
        limits.put(RateLimitedAction.JOB_CREATE, jobCreate);
        limits.put(RateLimitedAction.PROPOSAL_CREATE, proposalCreate);
        limits.put(RateLimitedAction.JOB_APPLICATION_CREATE, jobApplicationCreate);
        limits.put(RateLimitedAction.CHAT_MESSAGE_SEND, chatMessageSend);
        limits.put(RateLimitedAction.PAYMENT_PREPARE, paymentPrepare);
        return limits;
    }

    private static Map<RateLimitedAction, Integer> checkedLimits(Map<RateLimitedAction, Integer> limits) {
        Map<RateLimitedAction, Integer> checked = new EnumMap<>(limits);
        for (RateLimitedAction action : RateLimitedAction.values()) {
            Integer limit = checked.get(action);
            if (limit == null || limit < 1) {
                throw new IllegalArgumentException("Rate limit must be positive: " + action);
            }
        }
        return checked;
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
