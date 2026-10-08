package com.gakkum.backend.domain.notification.client;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.domain.Range.Bound;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import com.gakkum.backend.domain.notification.config.NotificationStreamProperties;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;
import com.gakkum.backend.domain.notification.service.NotificationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotificationStreamConsumer {

    private final StreamOperations<String, String, String> streams;
    private final NotificationService notificationService;
    private final NotificationStreamProperties properties;
    private final String consumerName = UUID.randomUUID().toString();
    private boolean groupReady;
    private String pendingCursor;

    public NotificationStreamConsumer(StringRedisTemplate redisTemplate, NotificationService notificationService,
            NotificationStreamProperties properties) {
        this.streams = redisTemplate.opsForStream();
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${notification.stream.poll-interval:1s}")
    public void poll() {
        try {
            ensureGroup();
            retryPending();
            process(streams.read(Consumer.from(properties.group(), consumerName),
                    StreamReadOptions.empty().count(properties.batchSize()),
                    StreamOffset.create(properties.key(), ReadOffset.lastConsumed())));
        } catch (RuntimeException exception) {
            // Redis 재시작 등으로 그룹이 사라졌다면 다음 폴링에서 다시 만든다.
            groupReady = false;
            log.warn("알림 Stream 폴링 실패: {}", exception.getClass().getSimpleName());
        }
    }

    private void ensureGroup() {
        if (groupReady) {
            return;
        }
        try {
            // 그룹 생성 전에 발행된 이벤트도 읽는다. 스트림이 없으면 함께 생성한다.
            streams.createGroup(properties.key(), ReadOffset.from("0-0"), properties.group());
        } catch (DataAccessException exception) {
            String message = exception.getMostSpecificCause().getMessage();
            if (message == null || !message.startsWith("BUSYGROUP")) {
                throw exception;
            }
        }
        groupReady = true;
    }

    private void retryPending() {
        // Lettuce의 XPENDING은 Bound.exclusive를 직렬화하지 않아 Redis의 제외 접두사를 직접 전달한다.
        Range<String> range = pendingCursor == null ? Range.unbounded()
                : Range.of(Bound.inclusive("(" + pendingCursor), Bound.unbounded());
        PendingMessages pending = streams.pending(properties.key(), properties.group(), range,
                properties.batchSize(), properties.retryIdle());
        if (pending == null || pending.isEmpty()) {
            pendingCursor = null;
            return;
        }
        List<RecordId> ids = pending.stream().map(PendingMessage::getId).toList();
        // 실패 메시지가 앞에 쌓여 있어도 뒤쪽 미처리 메시지를 순환해서 확인한다.
        pendingCursor = ids.size() < properties.batchSize() ? null : ids.getLast().getValue();
        process(streams.claim(properties.key(), properties.group(), consumerName, properties.retryIdle(),
                ids.toArray(RecordId[]::new)));
    }

    private void process(List<MapRecord<String, String, String>> records) {
        if (records == null) {
            return;
        }
        for (MapRecord<String, String, String> record : records) {
            try {
                // 별도 Spring 프록시의 트랜잭션 커밋이 끝난 뒤에만 ACK한다.
                notificationService.storeEvent(NotificationEvent.from(record.getValue()));
                streams.acknowledge(properties.key(), properties.group(), record.getId());
            } catch (RuntimeException exception) {
                // 본문·수신자 정보는 로그에 남기지 않는다. 실패 메시지는 pending에 보존한다.
                log.warn("알림 이벤트 처리 실패: recordId={}, error={}", record.getId(),
                        exception.getClass().getSimpleName());
            }
        }
    }
}
