package com.gakkum.backend.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.notification.dto.NotificationCommandDto.GetNotificationsCommand;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;
import com.gakkum.backend.domain.notification.entity.Notification;
import com.gakkum.backend.domain.notification.repository.NotificationRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /** Stream 재전달은 같은 이벤트·수신자 조합으로 저장하지 않는다. 반환 전에 트랜잭션이 커밋되어야 ACK할 수 있다. */
    @Transactional
    public int storeEvent(NotificationEvent event) {
        return notificationRepository.insertIfAbsent(event.toEntity());
    }

    /**
     * 둘러보기 예시 데이터의 알림을 Stream 을 거치지 않고 바로 저장한다. readAt 이 있으면 그 시각에 읽은 알림으로 둔다.
     * 같은 이벤트·수신자 조합은 한 번만 만들 수 있으므로 새 데모 세션을 채울 때만 부른다.
     */
    @Transactional
    public Notification storeDemoSample(NotificationEvent event, LocalDateTime readAt) {
        Notification notification = event.toEntity();
        if (readAt != null) {
            notification.markRead(readAt);
        }
        return notificationRepository.save(notification);
    }

    /**
     * 수신자의 알림을 읽음 여부와 무관하게 최신순으로 읽는다. 커서가 있으면 그 알림 뒤부터 읽는다.
     * 호출하는 쪽이 다음 페이지 여부를 알 수 있도록 요청 크기보다 하나 더 읽는다. 읽음 상태는 바꾸지 않는다.
     */
    @Transactional(readOnly = true)
    public List<Notification> getNotifications(String recipientUserId, GetNotificationsCommand command) {
        Limit limit = Limit.of(command.getSize() + 1);
        if (command.getCursorCreatedAt() == null) {
            return notificationRepository.findByRecipientUserIdOrderByCreatedAtDescIdDesc(recipientUserId, limit);
        }
        return notificationRepository.findPageAfterCursor(
                recipientUserId, command.getCursorCreatedAt(), command.getCursorId(), limit);
    }

    @Transactional(readOnly = true)
    public long countUnread(String recipientUserId) {
        return notificationRepository.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
    }

    /**
     * 본인 알림 행을 잠근 뒤 최초 읽음 시각을 기록한다. 이미 읽은 알림은 기존 시각을 그대로 둔다.
     * 없는 알림과 다른 수신자의 알림은 구분하지 않고 404로 거부한다.
     */
    @Transactional
    public Notification markRead(String recipientUserId, Long notificationId) {
        Notification notification = notificationRepository
                .findLockedByIdAndRecipientUserId(notificationId, recipientUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(now());
        return notification;
    }

    /** 수신자의 미읽음 알림을 UPDATE 한 번으로 읽음 처리하고 바꾼 행 수를 반환한다. 이미 읽은 알림의 시각은 바꾸지 않는다. */
    @Transactional
    public int markAllRead(String recipientUserId) {
        return notificationRepository.markAllRead(recipientUserId, now());
    }

    // createdAt과 같은 JVM 기본 시간대로 읽음 시각을 기록한다.
    // PostgreSQL timestamp 정밀도(마이크로초)에 맞춰 반환값과 저장값이 어긋나지 않게 한다
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
