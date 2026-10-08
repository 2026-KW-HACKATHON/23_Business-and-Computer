package com.gakkum.backend.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.application.notification.dto.NotificationCursor;
import com.gakkum.backend.application.notification.dto.NotificationListRequest;
import com.gakkum.backend.application.notification.facade.NotificationFacade;
import com.gakkum.backend.config.ClockConfig;
import com.gakkum.backend.domain.notification.dto.NotificationCommandDto.GetNotificationsCommand;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationListResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationReadAllResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationReadResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationResult;
import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;
import com.gakkum.backend.domain.notification.service.NotificationService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

/**
 * 읽음 시각의 커밋과 행 잠금을 실제로 확인해야 하므로 클래스 트랜잭션을 끄고 직접 데이터를 정리한다.
 * 요청마다 별도 트랜잭션으로 실행되고, 운영 데이터를 건드리지 않도록 로컬 PostgreSQL에서만 실행한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ NotificationFacade.class, NotificationService.class, ClockConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("알림 조회·읽음 PostgreSQL 통합 (본인 범위·커서 페이지·읽음 보존·동시성)")
class NotificationPersistenceIntegrationTest {

    private static final String ME = "TEST_NOTIFICATION_ME";
    private static final String OTHER = "TEST_NOTIFICATION_OTHER";
    private static final String MY_USER_ID = "01K58M6PJV8VAJMXHBHJ2NTFM1";
    private static final String OTHER_USER_ID = "01K58M6PJV8VAJMXHBHJ2NTFM2";
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 7, 10, 0, 0, 123_456_000);
    private static final LocalDateTime EARLIER_READ_AT = LocalDateTime.of(2026, 10, 1, 9, 0, 0, 654_321_000);

    @Autowired
    private NotificationFacade notificationFacade;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private UserService userService;

    @BeforeEach
    void setUp() {
        deleteTestNotifications();
        when(userService.getActiveUser(ME)).thenReturn(
                User.builder().id(MY_USER_ID).username(ME).role(UserRole.STUDENT).build());
        when(userService.getActiveUser(OTHER)).thenReturn(
                User.builder().id(OTHER_USER_ID).username(OTHER).role(UserRole.OWNER).build());
    }

    @AfterEach
    void deleteTestNotifications() {
        jdbcTemplate.update("delete from notifications where recipient_user_id in (?, ?)", MY_USER_ID, OTHER_USER_ID);
    }

    @Test
    @DisplayName("PostgreSQL에서 목록은 본인 알림만 생성 시각 내림차순·같은 시각은 ID 내림차순으로 내리고 커서로 겹침·누락 없이 이어 조회한다")
    void listsOwnNotificationsInOrderAcrossPages() {
        long a = insert(MY_USER_ID, BASE, null);
        long b = insert(MY_USER_ID, BASE, EARLIER_READ_AT);
        long c = insert(MY_USER_ID, BASE, null);
        long older = insert(MY_USER_ID, BASE.minusNanos(1_000), null);
        long newer = insert(MY_USER_ID, BASE.plusNanos(1_000), null);
        insert(OTHER_USER_ID, BASE, null);
        insert(OTHER_USER_ID, BASE.plusDays(1), null);

        // 같은 시각 묶음(c, b, a)의 중간에 페이지 경계가 놓인다
        NotificationListResult first = list(ME, 2, null);
        assertThat(ids(first)).containsExactly(newer, c);
        assertThat(first.getNextCursor()).isNotNull();

        NotificationListResult second = list(ME, 2, first.getNextCursor());
        assertThat(ids(second)).containsExactly(b, a);
        assertThat(second.getNextCursor()).isNotNull();

        NotificationListResult last = list(ME, 2, second.getNextCursor());
        assertThat(ids(last)).containsExactly(older);
        assertThat(last.getNextCursor()).isNull();

        // 남은 알림 수가 요청 크기와 같으면 다음 페이지가 없다
        NotificationListResult all = list(ME, 5, null);
        assertThat(ids(all)).containsExactly(newer, c, b, a, older);
        assertThat(all.getNextCursor()).isNull();
        assertThat(all.getItems().get(2).getReadAt()).isEqualTo(EARLIER_READ_AT);
        assertThat(all.getItems().get(2).getCreatedAt()).isEqualTo(BASE);
        assertThat(all.getItems().get(4).getCreatedAt()).isEqualTo(BASE.minusNanos(1_000));

        NotificationListResult four = list(ME, 4, null);
        assertThat(ids(four)).containsExactly(newer, c, b, a);
        assertThat(ids(list(ME, 4, four.getNextCursor()))).containsExactly(older);
    }

    @Test
    @DisplayName("PostgreSQL에서 커서가 허용하는 가장 이른·늦은 시각은 쿼리가 처리하고 커서가 거부하는 범위 밖 시각은 DB도 처리하지 못한다")
    void acceptsCursorTimesWithinDatabaseRange() {
        long first = insert(MY_USER_ID, BASE.minusMinutes(1), null);
        long second = insert(MY_USER_ID, BASE, null);
        LocalDateTime earliest = LocalDateTime.of(-4712, 1, 1, 0, 0);
        LocalDateTime latest = LocalDateTime.of(294276, 12, 31, 23, 59, 59, 999_999_000);

        assertThat(ids(list(ME, 20, NotificationCursor.of(latest, 1L).encode()))).containsExactly(second, first);
        assertThat(ids(list(ME, 20, NotificationCursor.of(earliest, 1L).encode()))).isEmpty();

        // 커서 검증이 없으면 쿼리까지 전달되어 DB 오류가 되는 입력이다
        for (LocalDateTime outOfRange : List.of(latest.plusNanos(1_000), LocalDateTime.of(999_999_999, 1, 1, 0, 0),
                LocalDateTime.of(-999_999_999, 1, 1, 0, 0))) {
            assertThatThrownBy(() -> notificationFacade.getNotifications(
                    GetNotificationsCommand.of(ME, 20, outOfRange, 1L)))
                    .as(outOfRange.toString()).isInstanceOf(DataAccessException.class);
            assertInvalidCursor(NotificationCursor.of(outOfRange, 1L).encode());
        }
    }

    @Test
    @DisplayName("PostgreSQL에서 알림이 없는 사용자의 목록은 빈 목록과 null 커서이고 미읽음 개수는 0이다")
    void returnsEmptyListForUserWithoutNotifications() {
        insert(OTHER_USER_ID, BASE, null);

        NotificationListResult result = list(ME, 20, null);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getNextCursor()).isNull();
        assertThat(unreadCount(ME)).isZero();
    }

    @Test
    @DisplayName("PostgreSQL에서 목록과 미읽음 개수를 조회해도 미읽음 상태는 그대로이고 페이지 사이에 읽음 처리해도 다음 페이지가 어긋나지 않는다")
    void readingListsDoesNotMarkRead() {
        long first = insert(MY_USER_ID, BASE.minusMinutes(3), null);
        long second = insert(MY_USER_ID, BASE.minusMinutes(2), null);
        long third = insert(MY_USER_ID, BASE.minusMinutes(1), null);

        NotificationListResult page = list(ME, 2, null);
        assertThat(ids(page)).containsExactly(third, second);
        assertThat(unreadCount(ME)).isEqualTo(3);
        assertThat(list(ME, 20, null).getItems()).extracting(NotificationResult::getReadAt).containsOnlyNulls();
        assertThat(count("select count(*) from notifications where recipient_user_id = ? and read_at is not null",
                MY_USER_ID)).isZero();

        // 읽음 변경은 정렬 키(생성 시각·ID)를 바꾸지 않는다
        notificationFacade.markRead(ME, second);
        notificationFacade.markRead(ME, first);
        assertThat(ids(list(ME, 2, page.getNextCursor()))).containsExactly(first);
    }

    @Test
    @DisplayName("PostgreSQL에서 미읽음 개수는 본인 미읽음만 세고 개별 읽음마다 한 번씩, 모두 읽음 뒤에는 0으로 줄어든다")
    void countsOwnUnreadAndDecreasesOnRead() {
        long target = insert(MY_USER_ID, BASE, null);
        insert(MY_USER_ID, BASE, null);
        insert(MY_USER_ID, BASE, null);
        insert(MY_USER_ID, BASE, EARLIER_READ_AT);
        insert(OTHER_USER_ID, BASE, null);
        insert(OTHER_USER_ID, BASE, null);

        assertThat(unreadCount(ME)).isEqualTo(3);
        assertThat(unreadCount(OTHER)).isEqualTo(2);

        notificationFacade.markRead(ME, target);
        assertThat(unreadCount(ME)).isEqualTo(2);
        notificationFacade.markRead(ME, target);
        assertThat(unreadCount(ME)).isEqualTo(2);

        notificationFacade.markAllRead(ME);
        assertThat(unreadCount(ME)).isZero();
        assertThat(unreadCount(OTHER)).isEqualTo(2);
    }

    @Test
    @DisplayName("PostgreSQL에서 개별 읽음은 최초 읽음 시각을 저장하고 재요청에도 그 시각을 유지하며 생성 시각은 바꾸지 않는다")
    void recordsFirstReadTimeOnce() {
        long id = insert(MY_USER_ID, BASE, null);

        NotificationReadResult first = notificationFacade.markRead(ME, id);
        assertThat(first.getNotificationId()).isEqualTo(id);
        assertThat(first.getReadAt()).isNotNull().isEqualTo(readAt(id));

        NotificationReadResult again = notificationFacade.markRead(ME, id);
        assertThat(again.getReadAt()).isEqualTo(first.getReadAt());
        assertThat(readAt(id)).isEqualTo(first.getReadAt());
        assertThat(jdbcTemplate.queryForObject("select created_at from notifications where id = ?",
                LocalDateTime.class, id)).isEqualTo(BASE);
    }

    @Test
    @DisplayName("PostgreSQL에서 다른 사용자의 알림과 없는 알림의 개별 읽음은 같은 NOTIFICATION_404이고 대상 알림을 바꾸지 않는다")
    void rejectsOthersAndMissingNotification() {
        long others = insert(OTHER_USER_ID, BASE, null);
        long missing = count("select coalesce(max(id), 0) from notifications") + 1_000_000;

        assertNotFound(() -> notificationFacade.markRead(ME, others));
        assertNotFound(() -> notificationFacade.markRead(ME, missing));

        assertThat(readAt(others)).isNull();
        assertThat(unreadCount(OTHER)).isEqualTo(1);
    }

    @Test
    @DisplayName("PostgreSQL에서 모두 읽음은 조회하지 않은 페이지까지 본인 미읽음만 같은 시각으로 바꾸고 기존 읽음 시각과 타인 알림을 보존한다")
    void marksOnlyOwnUnreadNotifications() {
        List<Long> unread = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            unread.add(insert(MY_USER_ID, BASE.minusMinutes(i), null));
        }
        long alreadyRead = insert(MY_USER_ID, BASE, EARLIER_READ_AT);
        long others = insert(OTHER_USER_ID, BASE, null);
        long othersRead = insert(OTHER_USER_ID, BASE, EARLIER_READ_AT);
        // 첫 페이지만 조회한 상태에서 호출한다
        assertThat(list(ME, 20, null).getNextCursor()).isNotNull();

        NotificationReadAllResult result = notificationFacade.markAllRead(ME);

        assertThat(result.getUpdatedCount()).isEqualTo(25);
        assertThat(unread).extracting(this::readAt).doesNotContainNull().containsOnly(readAt(unread.get(0)));
        assertThat(readAt(alreadyRead)).isEqualTo(EARLIER_READ_AT);
        assertThat(readAt(others)).isNull();
        assertThat(readAt(othersRead)).isEqualTo(EARLIER_READ_AT);

        // 반복 호출은 아무것도 바꾸지 않는다
        LocalDateTime firstReadAt = readAt(unread.get(0));
        assertThat(notificationFacade.markAllRead(ME).getUpdatedCount()).isZero();
        assertThat(readAt(unread.get(0))).isEqualTo(firstReadAt);
    }

    @Test
    @DisplayName("PostgreSQL에서 대상별 읽음은 본인의 그 대상·그 종류 미읽음만 바꾸고 이미 읽은 시각·다른 대상·다른 종류·다른 사용자는 그대로 둔다")
    void marksOnlyMatchingTargetRead() {
        long target = insertTyped(MY_USER_ID, "JOB_REVIEW_REQUESTED", "42", null);
        long alreadyRead = insertTyped(MY_USER_ID, "JOB_REVIEW_REQUESTED", "42", EARLIER_READ_AT);
        long otherJob = insertTyped(MY_USER_ID, "JOB_REVIEW_REQUESTED", "43", null);
        long otherType = insert(MY_USER_ID, BASE, null);
        long others = insertTyped(OTHER_USER_ID, "JOB_REVIEW_REQUESTED", "42", null);

        int updated = notificationService.markTargetRead(MY_USER_ID, NotificationType.JOB_REVIEW_REQUESTED,
                NotificationTargetType.JOB, "42");

        assertThat(updated).isEqualTo(1);
        assertThat(readAt(target)).isNotNull();
        assertThat(readAt(alreadyRead)).isEqualTo(EARLIER_READ_AT);
        assertThat(readAt(otherJob)).isNull();
        assertThat(readAt(otherType)).isNull();
        assertThat(readAt(others)).isNull();
        // 반복 호출은 아무것도 바꾸지 않는다
        LocalDateTime firstReadAt = readAt(target);
        assertThat(notificationService.markTargetRead(MY_USER_ID, NotificationType.JOB_REVIEW_REQUESTED,
                NotificationTargetType.JOB, "42")).isZero();
        assertThat(readAt(target)).isEqualTo(firstReadAt);
    }

    @Test
    @DisplayName("PostgreSQL에서 처리할 알림이 없는 사용자의 모두 읽음은 변경 수 0으로 성공한다")
    void marksNothingWithoutUnreadNotifications() {
        insert(OTHER_USER_ID, BASE, null);

        assertThat(notificationFacade.markAllRead(ME).getUpdatedCount()).isZero();
        assertThat(unreadCount(OTHER)).isEqualTo(1);
    }

    @Test
    @DisplayName("PostgreSQL에서 같은 알림을 동시에 읽음 처리해도 모든 요청이 저장된 최초 읽음 시각 하나를 반환한다")
    void keepsFirstReadTimeForConcurrentReads() throws Exception {
        long id = insert(MY_USER_ID, BASE, null);

        List<NotificationReadResult> results = runConcurrently(repeat(8,
                () -> notificationFacade.markRead(ME, id)));

        LocalDateTime stored = readAt(id);
        assertThat(stored).isNotNull();
        assertThat(results).extracting(NotificationReadResult::getReadAt).containsOnly(stored);
    }

    @Test
    @DisplayName("PostgreSQL에서 개별 읽음이 먼저 잠근 알림은 그 잠금을 기다린 모두 읽음이 덮어쓰지 않고 나머지 미읽음만 바꾼다")
    void readAllDoesNotOverwriteConcurrentSingleRead() throws Exception {
        long target = insert(MY_USER_ID, BASE, null);
        long rest1 = insert(MY_USER_ID, BASE, null);
        long rest2 = insert(MY_USER_ID, BASE, null);

        Contended<NotificationReadResult, NotificationReadAllResult> results = runSecondBlockedByFirst(
                () -> notificationFacade.markRead(ME, target),
                () -> notificationFacade.markAllRead(ME));

        assertThat(results.second().getUpdatedCount()).isEqualTo(2);
        assertThat(readAt(target)).isEqualTo(results.first().getReadAt());
        assertThat(readAt(rest1)).isNotNull().isEqualTo(readAt(rest2));
    }

    @Test
    @DisplayName("PostgreSQL에서 모두 읽음이 먼저 바꾼 알림의 개별 읽음은 그 잠금을 기다렸다가 모두 읽음의 시각을 그대로 반환한다")
    void singleReadReturnsTimeOfConcurrentReadAll() throws Exception {
        long target = insert(MY_USER_ID, BASE, null);
        long rest = insert(MY_USER_ID, BASE, null);

        Contended<NotificationReadAllResult, NotificationReadResult> results = runSecondBlockedByFirst(
                () -> notificationFacade.markAllRead(ME),
                () -> notificationFacade.markRead(ME, target));

        assertThat(results.first().getUpdatedCount()).isEqualTo(2);
        assertThat(readAt(target)).isNotNull().isEqualTo(readAt(rest));
        assertThat(results.second().getReadAt()).isEqualTo(readAt(target));
    }

    @Test
    @DisplayName("PostgreSQL에서 개별 읽음과 모두 읽음을 섞어 동시에 요청해도 알림마다 최초 읽음 시각 하나만 남고 기존 읽음 시각은 유지된다")
    void keepsFirstReadTimeForMixedConcurrentReads() throws Exception {
        List<Long> unread = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            unread.add(insert(MY_USER_ID, BASE.minusMinutes(i), null));
        }
        long alreadyRead = insert(MY_USER_ID, BASE, EARLIER_READ_AT);
        long others = insert(OTHER_USER_ID, BASE, null);

        List<Callable<Object>> actions = new ArrayList<>();
        actions.add(() -> notificationFacade.markAllRead(ME));
        for (Long id : unread) {
            actions.add(() -> notificationFacade.markRead(ME, id));
        }
        actions.add(() -> notificationFacade.markAllRead(ME));
        actions.add(() -> notificationFacade.markRead(ME, alreadyRead));
        List<Object> results = runConcurrently(actions);

        int updatedByReadAll = 0;
        for (Object result : results) {
            if (result instanceof NotificationReadAllResult all) {
                updatedByReadAll += all.getUpdatedCount();
            } else if (result instanceof NotificationReadResult single) {
                // 요청이 받은 시각은 그 알림에 저장된 시각과 같다. 뒤따른 요청이 덮어썼다면 어긋난다
                assertThat(single.getReadAt()).isEqualTo(readAt(single.getNotificationId()));
            }
        }
        assertThat(updatedByReadAll).isBetween(0, 20);
        assertThat(unreadCount(ME)).isZero();
        assertThat(readAt(alreadyRead)).isEqualTo(EARLIER_READ_AT);
        assertThat(readAt(others)).isNull();
    }

    @Test
    @DisplayName("PostgreSQL에서 모두 읽음의 UPDATE 시점에 커밋되지 않았던 새 알림은 미읽음으로 남는다")
    void leavesNotificationCommittedAfterReadAllUnread() throws Exception {
        insert(MY_USER_ID, BASE, null);
        insert(MY_USER_ID, BASE, null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            // 새 알림을 저장한 트랜잭션이 모두 읽음이 끝난 뒤에 커밋한다
            Future<Long> arriving = executor.submit(() -> transactionTemplate.execute(status -> {
                long id = insert(MY_USER_ID, BASE.plusMinutes(1), null);
                inserted.countDown();
                await(release);
                return id;
            }));
            await(inserted);

            assertThat(notificationFacade.markAllRead(ME).getUpdatedCount()).isEqualTo(2);
            release.countDown();
            long arrived = arriving.get(30, TimeUnit.SECONDS);

            assertThat(readAt(arrived)).isNull();
            assertThat(unreadCount(ME)).isEqualTo(1);
            assertThat(ids(list(ME, 1, null))).containsExactly(arrived);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private NotificationListResult list(String username, int size, String cursor) {
        return notificationFacade.getNotifications(NotificationListRequest.of(size, cursor).toCommand(username));
    }

    private static List<Long> ids(NotificationListResult result) {
        return result.getItems().stream().map(NotificationResult::getId).toList();
    }

    private long unreadCount(String username) {
        return notificationFacade.getUnreadCount(username).getUnreadCount();
    }

    private long insert(String recipientUserId, LocalDateTime createdAt, LocalDateTime readAt) {
        Long id = jdbcTemplate.queryForObject("""
                insert into notifications
                    (event_id, recipient_user_id, type, title, body, target_type, target_id, read_at, created_at)
                values (cast(? as uuid), ?, 'JOB_APPLICATION_RECEIVED', '새 지원자가 있어요', '본문', 'JOB', '42',
                    cast(? as timestamp), ?)
                returning id
                """, Long.class, UUID.randomUUID().toString(), recipientUserId, readAt, createdAt);
        return id == null ? 0 : id;
    }

    private long insertTyped(String recipientUserId, String type, String targetId, LocalDateTime readAt) {
        Long id = jdbcTemplate.queryForObject("""
                insert into notifications
                    (event_id, recipient_user_id, type, title, body, target_type, target_id, read_at, created_at)
                values (cast(? as uuid), ?, ?, '제목', '본문', 'JOB', ?, cast(? as timestamp), ?)
                returning id
                """, Long.class, UUID.randomUUID().toString(), recipientUserId, type, targetId, readAt, BASE);
        return id == null ? 0 : id;
    }

    private LocalDateTime readAt(long id) {
        return jdbcTemplate.queryForObject("select read_at from notifications where id = ?", LocalDateTime.class, id);
    }

    private long count(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    private void assertInvalidCursor(String cursor) {
        assertThatThrownBy(() -> list(ME, 20, cursor))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("다른 요청을 기다리다 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    /**
     * 선행 요청이 잠금을 쥔 채 커밋하지 않은 동안 후행 요청을 시작하고, 후행 세션이 선행 세션의 잠금을 기다리는 것을 확인한 뒤에
     * 선행 요청을 커밋한다. 파사드 트랜잭션이 바깥 트랜잭션에 참여하므로 두 요청 모두 자기 세션의 PID를 같은 트랜잭션에서 읽는다.
     */
    private <A, B> Contended<A, B> runSecondBlockedByFirst(Supplier<A> first, Supplier<B> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstExecuted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        try {
            Future<A> holder = executor.submit(() -> transactionTemplate.execute(status -> {
                firstPid.set(backendPid());
                A result = first.get();
                firstExecuted.countDown();
                await(release);
                return result;
            }));
            await(firstExecuted);

            Future<B> waiter = executor.submit(() -> transactionTemplate.execute(status -> {
                secondPid.set(backendPid());
                secondStarted.countDown();
                return second.get();
            }));
            await(secondStarted);
            awaitBlockedBy(firstPid.get(), secondPid.get(), waiter);
            assertThat(holder.isDone()).isFalse();

            release.countDown();
            return new Contended<>(holder.get(30, TimeUnit.SECONDS), waiter.get(30, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private record Contended<A, B>(A first, B second) {
    }

    // 현재 트랜잭션이 쓰는 DB 세션의 PID
    private int backendPid() {
        Integer pid = jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class);
        return pid == null ? 0 : pid;
    }

    /** 후행 세션이 선행 세션에 막혀 잠금을 기다릴 때까지 기다린다. 후행 요청이 기다리지 않고 끝나거나 제한 시간 안에 막히지 않으면 실패한다. */
    private void awaitBlockedBy(int blockingPid, int blockedPid, Future<?> waiter) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Boolean blocked = jdbcTemplate.queryForObject(
                    "select ? = any(pg_blocking_pids(?))", Boolean.class, blockingPid, blockedPid);
            if (Boolean.TRUE.equals(blocked)) {
                return;
            }
            assertThat(waiter.isDone()).as("후행 요청이 선행 요청의 행 잠금을 기다리지 않고 끝났다").isFalse();
            Thread.sleep(20);
        }
        fail("후행 요청이 선행 요청의 행 잠금에서 대기하지 않았다");
    }

    private static <T> List<Callable<T>> repeat(int requests, Callable<T> action) {
        List<Callable<T>> actions = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            actions.add(action);
        }
        return actions;
    }

    // 모든 요청을 각자의 스레드와 트랜잭션에서 같은 순간에 시작한다
    private <T> List<T> runConcurrently(List<Callable<T>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> action : actions) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
