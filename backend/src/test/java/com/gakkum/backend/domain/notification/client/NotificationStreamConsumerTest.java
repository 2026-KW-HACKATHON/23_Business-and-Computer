package com.gakkum.backend.domain.notification.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.gakkum.backend.domain.notification.config.NotificationStreamProperties;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;
import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;
import com.gakkum.backend.domain.notification.service.NotificationService;

/** Redis 없이 Stream 연산을 흉내 내어, 메시지마다 저장·ACK 여부를 확인한다. */
@DisplayName("알림 Stream Consumer 메시지 처리")
class NotificationStreamConsumerTest {

    private static final String KEY = "notification-events";
    private static final String GROUP = "notification-persistence";

    private final StringRedisTemplate template = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, String, String> streams = mock(StreamOperations.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private NotificationStreamConsumer consumer;

    @BeforeEach
    void setUp() {
        when(template.<String, String>opsForStream()).thenReturn(streams);
        consumer = new NotificationStreamConsumer(template, notificationService,
                new NotificationStreamProperties(KEY, GROUP, 100, Duration.ofSeconds(30), 10000));
    }

    @Test
    @DisplayName("형식이 깨진 메시지(모르는 타입·필드 누락·잘못된 eventId)는 저장하지 않고 ACK해 다시 읽지 않으며, 같은 배치의 정상 메시지는 저장 뒤 ACK한다")
    void acknowledgesMalformedMessagesWithoutStoring() {
        NotificationEvent valid = event();
        Map<String, String> unknownType = new HashMap<>(valid.toMap());
        unknownType.put("type", "UNKNOWN_TYPE");
        Map<String, String> missingTitle = new HashMap<>(valid.toMap());
        missingTitle.remove("title");
        Map<String, String> badEventId = new HashMap<>(valid.toMap());
        badEventId.put("eventId", "not-a-uuid");
        givenNewRecords(record("1-0", unknownType), record("2-0", missingTitle), record("3-0", badEventId),
                record("4-0", valid.toMap()));

        consumer.poll();

        verify(notificationService).storeEvent(valid);
        verify(streams).acknowledge(KEY, GROUP, RecordId.of("1-0"));
        verify(streams).acknowledge(KEY, GROUP, RecordId.of("2-0"));
        verify(streams).acknowledge(KEY, GROUP, RecordId.of("3-0"));
        verify(streams).acknowledge(KEY, GROUP, RecordId.of("4-0"));
    }

    @Test
    @DisplayName("DB 장애처럼 다시 하면 될 수 있는 저장 실패는 ACK하지 않아 pending에 남긴다")
    void keepsMessagePendingWhenStoreFails() {
        NotificationEvent valid = event();
        givenNewRecords(record("1-0", valid.toMap()));
        doThrow(new DataAccessResourceFailureException("테스트 DB 장애")).when(notificationService).storeEvent(any());

        consumer.poll();

        verify(notificationService).storeEvent(valid);
        verify(streams, never()).acknowledge(KEY, GROUP, RecordId.of("1-0"));
    }

    @Test
    @DisplayName("형식이 깨진 메시지만 있으면 저장을 시도하지 않는다")
    void doesNotStoreWhenOnlyMalformedMessages() {
        givenNewRecords(record("1-0", Map.of("eventId", UUID.randomUUID().toString())));

        consumer.poll();

        verifyNoInteractions(notificationService);
        verify(streams).acknowledge(KEY, GROUP, RecordId.of("1-0"));
    }

    @SafeVarargs
    private void givenNewRecords(MapRecord<String, String, String>... records) {
        when(streams.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(records));
    }

    private MapRecord<String, String, String> record(String id, Map<String, String> fields) {
        return MapRecord.create(KEY, fields).withId(RecordId.of(id));
    }

    private NotificationEvent event() {
        return new NotificationEvent(UUID.randomUUID(), "01K58M6PJV8VAJMXHBHJ2PNB5C",
                NotificationType.JOB_APPLICATION_RECEIVED, "새로운 지원자가 있어요", "새 지원이 도착했습니다.",
                NotificationTargetType.JOB, "42");
    }
}
