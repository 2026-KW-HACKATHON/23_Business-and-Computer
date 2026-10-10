package com.gakkum.backend.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.application.notification.facade.NotificationFacade;
import com.gakkum.backend.config.ClockConfig;
import com.gakkum.backend.config.NotificationStreamConfig;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobApplicationCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.notification.client.NotificationEventPublisher;
import com.gakkum.backend.domain.notification.client.NotificationStreamConsumer;
import com.gakkum.backend.domain.notification.config.NotificationStreamProperties;
import com.gakkum.backend.domain.notification.dto.NotificationCommandDto.GetNotificationsCommand;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;
import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;
import com.gakkum.backend.domain.notification.service.NotificationService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

/** 별도 로컬 PostgreSQL과 테스트 전용 Stream에서 실제 커밋·ACK·재전달을 검증한다. */
@DataJpaTest(properties = "notification.stream.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ DataRedisAutoConfiguration.class, NotificationStreamConfig.class, NotificationService.class,
        NotificationFacade.class, ClockConfig.class, NotificationEventPublisher.class,
        JobFacade.class, JobService.class, OwnerService.class, StudentService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("알림 Redis Streams·PostgreSQL 통합")
class NotificationStreamIntegrationTest {

    private static final String RECIPIENT = "01K58M6PJV8VAJMXHBHJ2NSFM1";
    private static final String OTHER = "01K58M6PJV8VAJMXHBHJ2NSFM2";
    private static final String USERNAME = "NOTIFICATION_STREAM_TEST";
    private static final String APPLICANT = "NOTIFICATION_STREAM_APPLICANT";
    private static final String STREAM_KEY = "test-notification-events-" + UUID.randomUUID();

    @DynamicPropertySource
    static void configureStream(DynamicPropertyRegistry registry) {
        registry.add("notification.stream.key", () -> STREAM_KEY);
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private NotificationService service;

    @Autowired
    private NotificationFacade facade;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JobFacade jobFacade;

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private JobRepository jobRepository;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SpecialtyCategoryService specialtyCategoryService;

    @MockitoBean
    private SpecialtyService specialtyService;

    @MockitoBean
    private JobSubmissionFileStorageClient jobSubmissionFileStorageClient;

    @MockitoBean
    private ChatAttachmentPolicy chatAttachmentPolicy;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private CertificateService certificateService;

    @MockitoBean
    private ProposalService proposalService;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private ChatRoomService chatRoomService;

    private NotificationStreamProperties properties;
    private NotificationEventPublisher publisher;
    private Long applicationJobId;
    private Long ownerProfileId;
    private Long studentProfileId;

    @BeforeEach
    void setUp() {
        properties = new NotificationStreamProperties(STREAM_KEY,
                "notification-test-group", 100, Duration.ZERO, 10000);
        publisher = new NotificationEventPublisher(redisTemplate, properties);
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id(RECIPIENT).username(USERNAME).role(UserRole.STUDENT).build());
        jdbc.update("delete from notifications where recipient_user_id in (?, ?)", RECIPIENT, OTHER);
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(properties.key());
        jdbc.update("delete from notifications where recipient_user_id in (?, ?)", RECIPIENT, OTHER);
        if (applicationJobId != null) {
            jdbc.update("delete from job_applications where job_id = ?", applicationJobId);
            jobRepository.deleteById(applicationJobId);
        }
        if (ownerProfileId != null) {
            ownerRepository.deleteById(ownerProfileId);
        }
        if (studentProfileId != null) {
            studentRepository.deleteById(studentProfileId);
        }
    }

    @Test
    @DisplayName("그룹 생성 전에 발행한 7개 값을 미읽음으로 커밋하고 기존 목록에서 조회한 뒤 pending이 비어 있다")
    void publishesStoresAndListsNotification() {
        NotificationEvent event = event(UUID.randomUUID(), RECIPIENT, "새로운 지원자가 있어요");
        publisher.publish(event);

        consumer(service, properties).poll();

        var items = facade.getNotifications(GetNotificationsCommand.of(USERNAME, 20, null, null)).getItems();
        assertThat(items).hasSize(1);
        var item = items.getFirst();
        assertThat(item.getType()).isEqualTo(event.type());
        assertThat(item.getTitle()).isEqualTo(event.title());
        assertThat(item.getBody()).isEqualTo(event.body());
        assertThat(item.getTargetType()).isEqualTo(event.targetType());
        assertThat(item.getTargetId()).isEqualTo(event.targetId());
        assertThat(item.getReadAt()).isNull();
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(jdbc.queryForObject("select event_id from notifications where id = ?", UUID.class, item.getId()))
                .isEqualTo(event.eventId());
        assertThat(facade.getUnreadCount(USERNAME).getUnreadCount()).isEqualTo(1);
        assertThat(pendingCount()).isZero();
    }

    @Test
    @DisplayName("지원 API 성공 후 작성 사장님의 알림 목록에 의뢰 대상을 저장하고 중복 지원은 추가 발행하지 않는다")
    void connectsApplicationApiToOwnerNotification() throws Exception {
        givenApplicationJob();
        var mvc = MockMvcBuilders.standaloneSetup(new JobController(jobFacade))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = """
                {"summary":"지원합니다", "workPlan":"시안 제작", "deliveryMethod":"PDF 전달", "deadlineAndPenaltyAgreed":true}
                """;
        var authentication = new UsernamePasswordAuthenticationToken(APPLICANT, null);
        mvc.perform(post("/jobs/{jobId}/applications", applicationJobId).principal(authentication)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/jobs/{jobId}/applications", applicationJobId).principal(authentication)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertThat(redisTemplate.opsForStream().size(properties.key())).isEqualTo(1);

        consumer(service, properties).poll();

        var items = facade.getNotifications(GetNotificationsCommand.of(USERNAME, 20, null, null)).getItems();
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getType()).isEqualTo(NotificationType.JOB_APPLICATION_RECEIVED);
        assertThat(items.getFirst().getTargetType()).isEqualTo(NotificationTargetType.JOB);
        assertThat(items.getFirst().getTargetId()).isEqualTo(applicationJobId.toString());
        assertThat(items.getFirst().getReadAt()).isNull();
        assertThat(service.countUnread(OTHER)).isZero();
        assertThat(pendingCount()).isZero();
    }

    @Test
    @DisplayName("외부 트랜잭션 안의 지원은 커밋 전 발행하지 않고 커밋된 뒤에만 Redis에 발행한다")
    void publishesOnlyAfterEnclosingCommit() {
        givenApplicationJob();
        transactions.executeWithoutResult(status -> {
            jobFacade.createJobApplication(applicationCommand());
            assertThat(redisTemplate.opsForStream().size(properties.key())).isZero();
        });

        assertThat(redisTemplate.opsForStream().size(properties.key())).isEqualTo(1);
        consumer(service, properties).poll();
        assertThat(service.countUnread(RECIPIENT)).isEqualTo(1);
    }

    @Test
    @DisplayName("지원 저장 후 외부 트랜잭션이 롤백되면 지원서도 알림 이벤트도 남지 않는다")
    void doesNotPublishRolledBackApplication() {
        givenApplicationJob();
        transactions.executeWithoutResult(status -> {
            jobFacade.createJobApplication(applicationCommand());
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("select count(*) from job_applications where job_id = ?",
                Long.class, applicationJobId)).isZero();
        assertThat(redisTemplate.opsForStream().size(properties.key())).isZero();
        assertThat(service.countUnread(RECIPIENT)).isZero();
    }

    @Test
    @DisplayName("재발행해도 기존 내용·생성 시각·읽음 시각을 보존하고 같은 이벤트의 다른 수신자는 별도로 저장한다")
    void preservesExistingNotificationOnRedelivery() {
        UUID eventId = UUID.randomUUID();
        publisher.publish(event(eventId, RECIPIENT, "최초 제목"));
        var consumer = consumer(service, properties);
        consumer.poll();
        var original = facade.getNotifications(GetNotificationsCommand.of(USERNAME, 20, null, null))
                .getItems().getFirst();
        LocalDateTime readAt = facade.markRead(USERNAME, original.getId()).getReadAt();

        publisher.publish(event(eventId, RECIPIENT, "재전달 제목"));
        publisher.publish(event(eventId, OTHER, "다른 수신자"));
        consumer.poll();

        var result = facade.getNotifications(GetNotificationsCommand.of(USERNAME, 20, null, null)).getItems();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(original.getId());
        assertThat(result.getFirst().getTitle()).isEqualTo("최초 제목");
        assertThat(result.getFirst().getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(result.getFirst().getReadAt()).isEqualTo(readAt);
        assertThat(service.countUnread(OTHER)).isEqualTo(1);
        assertThat(pendingCount()).isZero();
    }

    @Test
    @DisplayName("같은 이벤트를 별도 DB 트랜잭션에서 동시에 저장해도 한 건만 삽입하고 두 요청 모두 성공한다")
    void handlesConcurrentDuplicateInserts() throws Exception {
        NotificationEvent event = event(UUID.randomUUID(), RECIPIENT, "동시 전달");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> store = () -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return service.storeEvent(event);
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(store);
            var second = executor.submit(store);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS) + second.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            start.countDown();
        }
        assertThat(service.countUnread(RECIPIENT)).isEqualTo(1);
    }

    @Test
    @DisplayName("DB 세션이 한국 시간대여도 알림 생성 시각은 기존 엔티티 계약대로 UTC로 저장한다")
    void storesCreatedAtInUtcRegardlessOfDatabaseTimezone() {
        NotificationEvent event = event(UUID.randomUUID(), RECIPIENT, "UTC 저장");
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        transactions.executeWithoutResult(status -> {
            jdbc.execute("set local time zone 'Asia/Seoul'");
            service.storeEvent(event);
        });

        LocalDateTime createdAt = jdbc.queryForObject(
                "select created_at from notifications where event_id = ?", LocalDateTime.class, event.eventId());
        assertThat(createdAt).isBetween(before, LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1));
    }

    @Test
    @DisplayName("DB 저장 실패는 ACK하지 않고 대기 시간을 지킨 뒤 새 Consumer가 다른 Consumer의 pending을 복구한다")
    void recoversFailedDeliveryAfterRestart() {
        publisher.publish(event(UUID.randomUUID(), RECIPIENT, "재처리"));
        NotificationService unavailable = mock(NotificationService.class);
        doThrow(new DataAccessResourceFailureException("테스트 DB 장애")).when(unavailable).storeEvent(any());
        consumer(unavailable, properties).poll();
        assertThat(pendingCount()).isEqualTo(1);
        assertThat(service.countUnread(RECIPIENT)).isZero();

        consumer(service, new NotificationStreamProperties(properties.key(), properties.group(), 100,
                Duration.ofDays(1), properties.maxLength())).poll();
        assertThat(pendingCount()).isEqualTo(1);
        assertThat(service.countUnread(RECIPIENT)).isZero();

        consumer(service, properties).poll();
        assertThat(pendingCount()).isZero();
        assertThat(service.countUnread(RECIPIENT)).isEqualTo(1);
    }

    @Test
    @DisplayName("DB 커밋 뒤 ACK가 실패해도 새 Consumer의 재처리는 DB를 중복 저장하지 않고 ACK를 완료한다")
    void recoversAckFailureAfterCommit() {
        publisher.publish(event(UUID.randomUUID(), RECIPIENT, "ACK 재시도"));
        StreamOperations<String, String, String> streams = spy(redisTemplate.<String, String>opsForStream());
        doThrow(new RedisSystemException("테스트 ACK 장애", new IllegalStateException()))
                .when(streams).acknowledge(eq(properties.key()), eq(properties.group()), any(RecordId.class));
        StringRedisTemplate failingTemplate = mock(StringRedisTemplate.class);
        when(failingTemplate.<String, String>opsForStream()).thenReturn(streams);
        new NotificationStreamConsumer(failingTemplate, service, properties).poll();
        assertThat(pendingCount()).isEqualTo(1);
        assertThat(service.countUnread(RECIPIENT)).isEqualTo(1);
        var original = facade.getNotifications(GetNotificationsCommand.of(USERNAME, 20, null, null))
                .getItems().getFirst();

        consumer(service, properties).poll();

        var result = facade.getNotifications(GetNotificationsCommand.of(USERNAME, 20, null, null)).getItems();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(original.getId());
        assertThat(result.getFirst().getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(pendingCount()).isZero();
    }

    @Test
    @DisplayName("형식 오류는 저장하지 않고 ACK하며, DB 제약 위반은 pending에 남기고 배치 앞쪽의 실패 메시지가 뒤쪽 복구를 막지 않는다")
    void skipsPoisonMessagesWithoutStarvingLaterPendingMessages() {
        var invalidFields = new HashMap<>(event(UUID.randomUUID(), RECIPIENT, "잘못된 타입").toMap());
        invalidFields.put("type", "UNKNOWN_TYPE");
        redisTemplate.<String, String>opsForStream().add(properties.key(), invalidFields);
        publisher.publish(event(UUID.randomUUID(), RECIPIENT, "x".repeat(256)));
        publisher.publish(event(UUID.randomUUID(), RECIPIENT, "정상 알림"));
        var singleBatch = new NotificationStreamProperties(properties.key(), properties.group(), 1, Duration.ZERO,
                properties.maxLength());
        NotificationService unavailable = mock(NotificationService.class);
        doThrow(new DataAccessResourceFailureException("테스트 DB 장애")).when(unavailable).storeEvent(any());
        var failedConsumer = consumer(unavailable, singleBatch);
        for (int i = 0; i < 3; i++) {
            failedConsumer.poll();
        }
        // 형식 오류 메시지는 DB 장애와 무관하게 처리할 수 없으므로 첫 폴링에서 ACK되어 pending에 남지 않는다
        assertThat(pendingCount()).isEqualTo(2);

        var recoveredConsumer = consumer(service, singleBatch);
        for (int i = 0; i < 3; i++) {
            recoveredConsumer.poll();
        }

        assertThat(pendingCount()).isEqualTo(1);
        assertThat(service.countUnread(RECIPIENT)).isEqualTo(1);
        assertThat(facade.getNotifications(GetNotificationsCommand.of(USERNAME, 20, null, null))
                .getItems().getFirst().getTitle()).isEqualTo("정상 알림");
    }

    private NotificationStreamConsumer consumer(NotificationService notificationService,
            NotificationStreamProperties settings) {
        return new NotificationStreamConsumer(redisTemplate, notificationService, settings);
    }

    private void givenApplicationJob() {
        ownerProfileId = ownerRepository.saveAndFlush(Owner.builder().userId(RECIPIENT)
                .businessNumber("TEST-" + UUID.randomUUID()).storeName("알림 테스트 매장").categoryId(1L).build()).getId();
        studentProfileId = studentRepository.saveAndFlush(Student.create(OTHER, "테스트대", "202699999",
                "테스트 전공", null, null, null)).getId();
        applicationJobId = jobRepository.saveAndFlush(Job.create(ownerProfileId, "지원 알림 테스트", "의뢰 설명",
                10000L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), 0, null)).getId();
        when(userService.getActiveUser(APPLICANT)).thenReturn(
                User.builder().id(OTHER).username(APPLICANT).name("김학생").role(UserRole.STUDENT).build());
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id(RECIPIENT).username(USERNAME).role(UserRole.OWNER).build());
    }

    private CreateJobApplicationCommand applicationCommand() {
        return CreateJobApplicationCommand.of(APPLICANT, applicationJobId, "지원합니다", "시안 제작", "PDF 전달");
    }

    private long pendingCount() {
        return redisTemplate.opsForStream().pending(properties.key(), properties.group()).getTotalPendingMessages();
    }

    private NotificationEvent event(UUID eventId, String recipient, String title) {
        return new NotificationEvent(eventId, recipient, NotificationType.JOB_APPLICATION_RECEIVED, title,
                "등록한 의뢰에 새로운 지원이 도착했습니다.", NotificationTargetType.JOB, "42");
    }
}
