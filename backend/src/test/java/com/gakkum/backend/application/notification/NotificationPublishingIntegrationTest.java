package com.gakkum.backend.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.application.proposal.facade.ProposalFacade;
import com.gakkum.backend.config.ClockConfig;
import com.gakkum.backend.config.NotificationStreamConfig;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.notification.client.NotificationEventPublisher;
import com.gakkum.backend.domain.notification.client.NotificationStreamConsumer;
import com.gakkum.backend.domain.notification.config.NotificationStreamProperties;
import com.gakkum.backend.domain.notification.service.NotificationService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalLikeResult;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;

/**
 * 업무 트랜잭션의 실제 커밋·롤백과 Redis 발행, Consumer 저장을 이어서 확인한다.
 * 운영 데이터를 건드리지 않도록 로컬 PostgreSQL과 테스트 전용 Stream에서만 실행하고 만든 행을 직접 지운다.
 */
@DataJpaTest(properties = "notification.stream.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ DataRedisAutoConfiguration.class, NotificationStreamConfig.class, NotificationService.class,
        ClockConfig.class, NotificationEventPublisher.class, ProposalFacade.class, ProposalService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("업무 기능의 알림 발행 Redis Streams·PostgreSQL 통합 (커밋 후 발행·롤백·중복 저장 방지·발행 실패)")
class NotificationPublishingIntegrationTest {

    private static final long OWNER_PROFILE_ID = 987_505L;
    private static final long PROPOSER_PROFILE_ID = 987_501L;
    private static final long LIKER_PROFILE_ID = 987_502L;
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2NPBOW";
    private static final String PROPOSER_USER_ID = "01K58M6PJV8VAJMXHBHJ2NPBST";
    private static final String LIKER_USER_ID = "01K58M6PJV8VAJMXHBHJ2NPBLK";
    private static final String LIKER_USERNAME = "TEST_NOTIFICATION_LIKER";
    private static final String STREAM_KEY = "test-notification-publishing-" + UUID.randomUUID();

    @DynamicPropertySource
    static void configureStream(DynamicPropertyRegistry registry) {
        registry.add("notification.stream.key", () -> STREAM_KEY);
    }

    @Autowired
    private ProposalFacade proposalFacade;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @MockitoSpyBean
    private NotificationEventPublisher publisher;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private OwnerService ownerService;

    @MockitoBean
    private SpecialtyService specialtyService;

    @MockitoBean
    private SpecialtyCategoryService specialtyCategoryService;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private ChatRoomService chatRoomService;

    private final List<Long> proposalIds = new ArrayList<>();
    private NotificationStreamProperties properties;

    @BeforeEach
    void setUp() {
        properties = new NotificationStreamProperties(STREAM_KEY, "notification-publishing-test-group", 100,
                Duration.ZERO);
        when(userService.getActiveUser(LIKER_USERNAME)).thenReturn(User.builder()
                .id(LIKER_USER_ID).username(LIKER_USERNAME).name("이공감").role(UserRole.STUDENT).build());
        when(studentService.findStudentProfileByUserId(LIKER_USER_ID)).thenReturn(Optional.of(
                Student.builder().id(LIKER_PROFILE_ID).userId(LIKER_USER_ID).build()));
        when(studentService.getStudentProfile(PROPOSER_PROFILE_ID)).thenReturn(
                Student.builder().id(PROPOSER_PROFILE_ID).userId(PROPOSER_USER_ID).build());
        when(ownerService.getOwnerProfileById(OWNER_PROFILE_ID)).thenReturn(
                Owner.builder().id(OWNER_PROFILE_ID).userId(OWNER_USER_ID).storeName("가꿈 카페").build());
        deleteNotifications();
    }

    @AfterEach
    void cleanUp() {
        reset(publisher);
        redisTemplate.delete(STREAM_KEY);
        deleteNotifications();
        for (Long proposalId : proposalIds) {
            jdbc.update("delete from proposal_likes where proposal_id = ?", proposalId);
            jdbc.update("delete from proposals where id = ?", proposalId);
        }
    }

    @Test
    @DisplayName("공감 9→10명은 커밋 뒤 사장님과 제안 학생에게 발행하고, 10→9→10 재도달로 다시 발행해도 저장된 알림은 수신자마다 한 건이다")
    void storesLikeMilestoneOncePerRecipientWhenReachedAgain() {
        Long proposalId = saveProposal(9);

        assertThat(proposalFacade.likeProposal(LIKER_USERNAME, proposalId).getLikeCount()).isEqualTo(10);
        assertThat(streamSize()).isEqualTo(2);
        assertThat(proposalFacade.unlikeProposal(LIKER_USERNAME, proposalId).getLikeCount()).isEqualTo(9);
        assertThat(streamSize()).isEqualTo(2);
        assertThat(proposalFacade.likeProposal(LIKER_USERNAME, proposalId).getLikeCount()).isEqualTo(10);
        assertThat(streamSize()).isEqualTo(4);

        consumer().poll();

        assertThat(storedNotifications(OWNER_USER_ID)).singleElement().satisfies(row -> {
            assertThat(row.get("type")).isEqualTo("PROPOSAL_LIKE_MILESTONE_REACHED");
            assertThat(row.get("target_type")).isEqualTo("PROPOSAL");
            assertThat(row.get("target_id")).isEqualTo(proposalId.toString());
            assertThat(row.get("title")).isEqualTo("받은 제안에 10명이 공감했어요");
            assertThat(row.get("body")).isEqualTo("학생 손님 10명이 '메뉴판 개선 제안' 제안에 공감했어요.");
            assertThat(row.get("read_at")).isNull();
        });
        assertThat(storedNotifications(PROPOSER_USER_ID)).singleElement().satisfies(row -> {
            assertThat(row.get("type")).isEqualTo("PROPOSAL_LIKE_MILESTONE_REACHED");
            assertThat(row.get("target_id")).isEqualTo(proposalId.toString());
            assertThat(row.get("title")).isEqualTo("내 제안에 10명이 공감했어요");
        });
        // 공감한 학생은 수신자가 아니다
        assertThat(storedNotifications(LIKER_USER_ID)).isEmpty();
        assertThat(pendingCount()).isZero();
    }

    @ParameterizedTest(name = "공감 {0}→{1}명")
    @ValueSource(ints = { 29, 49 })
    @DisplayName("공감 29→30명, 49→50명도 커밋 뒤 두 수신자에게 발행한다")
    void publishesLaterLikeMilestones(int likeCountBefore) {
        Long proposalId = saveProposal(likeCountBefore);

        proposalFacade.likeProposal(LIKER_USERNAME, proposalId);

        assertThat(streamSize()).isEqualTo(2);
        consumer().poll();
        assertThat(storedNotifications(OWNER_USER_ID)).singleElement().satisfies(row ->
                assertThat(row.get("title")).isEqualTo("받은 제안에 " + (likeCountBefore + 1) + "명이 공감했어요"));
        assertThat(storedNotifications(PROPOSER_USER_ID)).hasSize(1);
    }

    @ParameterizedTest(name = "공감 {0}명에서 요청")
    @ValueSource(ints = { 0, 8, 10, 19, 39, 50 })
    @DisplayName("기준 인원이 아닌 공감 수(20·40명 포함)와 중복 공감 요청은 발행하지 않는다")
    void doesNotPublishOutsideMilestones(int likeCountBefore) {
        Long proposalId = saveProposal(likeCountBefore);

        ProposalLikeResult liked = proposalFacade.likeProposal(LIKER_USERNAME, proposalId);
        // 이미 공감한 제안의 재요청은 공감 수가 기준 인원에 머물러 있어도 발행하지 않는다
        ProposalLikeResult likedAgain = proposalFacade.likeProposal(LIKER_USERNAME, proposalId);

        assertThat(liked.getLikeCount()).isEqualTo(likeCountBefore + 1);
        assertThat(likedAgain.getLikeCount()).isEqualTo(likeCountBefore + 1);
        assertThat(streamSize()).isZero();
    }

    @Test
    @DisplayName("기준 인원에 도달한 공감 뒤의 중복 공감 요청은 다시 발행하지 않는다")
    void doesNotPublishDuplicateLikeAtMilestone() {
        Long proposalId = saveProposal(9);

        proposalFacade.likeProposal(LIKER_USERNAME, proposalId);
        proposalFacade.likeProposal(LIKER_USERNAME, proposalId);

        assertThat(streamSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("바깥 트랜잭션 안에서는 커밋 전에 발행하지 않고, 롤백되면 공감도 알림 이벤트도 남지 않는다")
    void doesNotPublishBeforeCommitOrOnRollback() {
        Long proposalId = saveProposal(9);

        transactions.executeWithoutResult(status -> {
            proposalFacade.likeProposal(LIKER_USERNAME, proposalId);
            assertThat(streamSize()).isZero();
            status.setRollbackOnly();
        });

        assertThat(streamSize()).isZero();
        assertThat(jdbc.queryForObject("select like_count from proposals where id = ?", Integer.class, proposalId))
                .isEqualTo(9);
        assertThat(jdbc.queryForObject("select count(*) from proposal_likes where proposal_id = ?", Integer.class,
                proposalId)).isZero();
    }

    @Test
    @DisplayName("한 수신자의 Redis 발행이 실패해도 커밋된 공감은 성공으로 응답하고 다른 수신자에게는 발행한다")
    void keepsBusinessSuccessAndOtherRecipientWhenOnePublishFails() {
        Long proposalId = saveProposal(9);
        doThrow(new RedisConnectionFailureException("테스트 Redis 장애"))
                .when(publisher).publish(argThat(event -> OWNER_USER_ID.equals(event.recipientUserId())));

        ProposalLikeResult result = proposalFacade.likeProposal(LIKER_USERNAME, proposalId);

        assertThat(result.getLikeCount()).isEqualTo(10);
        assertThat(jdbc.queryForObject("select like_count from proposals where id = ?", Integer.class, proposalId))
                .isEqualTo(10);
        assertThat(streamSize()).isEqualTo(1);
        consumer().poll();
        assertThat(storedNotifications(OWNER_USER_ID)).isEmpty();
        assertThat(storedNotifications(PROPOSER_USER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("Redis가 모든 발행을 거부해도 커밋된 공감은 성공으로 응답한다")
    void keepsBusinessSuccessWhenRedisIsUnavailable() {
        Long proposalId = saveProposal(9);
        doThrow(new RedisConnectionFailureException("테스트 Redis 장애")).when(publisher).publish(argThat(event -> true));

        assertThat(proposalFacade.likeProposal(LIKER_USERNAME, proposalId).getLikeCount()).isEqualTo(10);

        assertThat(jdbc.queryForObject("select count(*) from proposal_likes where proposal_id = ?", Integer.class,
                proposalId)).isEqualTo(1);
        assertThat(streamSize()).isZero();
    }

    private Long saveProposal(int likeCount) {
        Proposal proposal = proposalRepository.saveAndFlush(Proposal.create(
                PROPOSER_PROFILE_ID, OWNER_PROFILE_ID, "메뉴판 개선 제안", "문제", "해결", "계획",
                50_000L, 3, 7, List.of(), null));
        proposalIds.add(proposal.getId());
        jdbc.update("update proposals set like_count = ? where id = ?", likeCount, proposal.getId());
        return proposal.getId();
    }

    private NotificationStreamConsumer consumer() {
        return new NotificationStreamConsumer(redisTemplate, notificationService, properties);
    }

    private long streamSize() {
        Long size = redisTemplate.opsForStream().size(STREAM_KEY);
        return size == null ? 0 : size;
    }

    private long pendingCount() {
        return redisTemplate.opsForStream().pending(STREAM_KEY, properties.group()).getTotalPendingMessages();
    }

    private List<Map<String, Object>> storedNotifications(String recipientUserId) {
        return jdbc.queryForList(
                "select type, title, body, target_type, target_id, read_at from notifications"
                        + " where recipient_user_id = ?", recipientUserId);
    }

    private void deleteNotifications() {
        jdbc.update("delete from notifications where recipient_user_id in (?, ?, ?)",
                OWNER_USER_ID, PROPOSER_USER_ID, LIKER_USER_ID);
    }
}
