package com.gakkum.backend.domain.notification.client;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.gakkum.backend.domain.notification.config.NotificationStreamProperties;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final NotificationStreamProperties properties;

    /** 업무 트랜잭션 커밋 뒤 호출한다. DB와 Redis 사이의 원자적 발행은 제공하지 않는다. */
    public RecordId publish(NotificationEvent event) {
        return redisTemplate.<String, String>opsForStream().add(properties.key(), event.toMap());
    }

    /** 트랜잭션이 있으면 커밋 뒤 발행하고, 이미 커밋된 Facade 호출은 즉시 발행한다. 롤백 때는 발행하지 않는다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publishCommitted(NotificationEvent event) {
        try {
            publish(event);
        } catch (DataAccessException exception) {
            // 지원 등 이미 완료된 업무의 성공 응답을 유지한다. 발행 실패 복구는 별도 범위다.
            log.warn("알림 이벤트 발행 실패: eventId={}, error={}", event.eventId(),
                    exception.getClass().getSimpleName());
        }
    }
}
