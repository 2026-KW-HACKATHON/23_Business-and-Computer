package com.gakkum.backend.application.notification.facade;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.notification.dto.NotificationCursor;
import com.gakkum.backend.domain.notification.dto.NotificationCommandDto.GetNotificationsCommand;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationListResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationReadAllResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationReadResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationResult;
import com.gakkum.backend.domain.notification.dto.NotificationQueryDto.NotificationUnreadCountResult;
import com.gakkum.backend.domain.notification.entity.Notification;
import com.gakkum.backend.domain.notification.service.NotificationService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.service.UserService;

import lombok.RequiredArgsConstructor;

/** 모든 기능은 역할(학생·사장님)과 무관하게 활성 사용자 본인이 받은 알림만 다룬다. */
@Component
@RequiredArgsConstructor
public class NotificationFacade {

    private final UserService userService;
    private final NotificationService notificationService;

    /**
     * 알림 목록. 읽음 여부와 무관하게 최신순으로 커서 뒤의 알림을 size+1개까지 읽어
     * 한 개가 남으면 다음 페이지가 있다고 보고 이번 페이지 마지막 알림으로 커서를 만든다. 읽음 상태는 바꾸지 않는다.
     */
    @Transactional(readOnly = true)
    public NotificationListResult getNotifications(GetNotificationsCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        List<Notification> notifications = notificationService.getNotifications(user.getId(), command);

        boolean hasNext = notifications.size() > command.getSize();
        List<Notification> page = hasNext ? notifications.subList(0, command.getSize()) : notifications;
        String nextCursor = null;
        if (hasNext) {
            Notification last = page.get(page.size() - 1);
            nextCursor = NotificationCursor.of(last.getCreatedAt(), last.getId()).encode();
        }
        return NotificationListResult.of(page.stream().map(NotificationResult::from).toList(), nextCursor);
    }

    /** 미읽음 알림 수. 읽음 상태는 바꾸지 않는다. */
    @Transactional(readOnly = true)
    public NotificationUnreadCountResult getUnreadCount(String username) {
        User user = userService.getActiveUser(username);
        return NotificationUnreadCountResult.of(notificationService.countUnread(user.getId()));
    }

    /**
     * 알림 한 건을 읽음 처리한다. 이미 읽은 알림의 재요청은 최초 읽음 시각을 그대로 반환한다.
     * 없는 알림과 다른 사용자의 알림은 같은 404로 거부한다.
     */
    @Transactional
    public NotificationReadResult markRead(String username, Long notificationId) {
        User user = userService.getActiveUser(username);
        return NotificationReadResult.from(notificationService.markRead(user.getId(), notificationId));
    }

    /**
     * 조회하지 않은 페이지까지 포함해 본인의 미읽음 알림을 모두 읽음 처리한다.
     * 대상이 없거나 반복 요청이면 변경 수 0으로 성공한다. 처리 이후 도착한 알림은 미읽음으로 남는다.
     */
    @Transactional
    public NotificationReadAllResult markAllRead(String username) {
        User user = userService.getActiveUser(username);
        return NotificationReadAllResult.of(notificationService.markAllRead(user.getId()));
    }
}
