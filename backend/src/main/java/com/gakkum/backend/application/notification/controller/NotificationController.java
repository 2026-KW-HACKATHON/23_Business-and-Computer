package com.gakkum.backend.application.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.notification.dto.NotificationListRequest;
import com.gakkum.backend.application.notification.dto.NotificationListResponse;
import com.gakkum.backend.application.notification.dto.NotificationReadAllResponse;
import com.gakkum.backend.application.notification.dto.NotificationReadResponse;
import com.gakkum.backend.application.notification.dto.NotificationUnreadCountResponse;
import com.gakkum.backend.application.notification.facade.NotificationFacade;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationFacade notificationFacade;

    /** 로그인한 사용자가 받은 알림을 읽음 여부와 무관하게 최신순으로 커서로 이어 조회하는 API. 읽음 상태는 바꾸지 않는다 */
    @GetMapping("/me/notifications")
    public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String cursor) {
        NotificationListRequest request = NotificationListRequest.of(size, cursor);
        NotificationListResponse response = NotificationListResponse.from(
                notificationFacade.getNotifications(request.toCommand(authentication.getName())));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 로그인한 사용자의 미읽음 알림 수를 조회하는 API. 읽음 상태는 바꾸지 않는다 */
    @GetMapping("/me/notifications/unread-count")
    public ResponseEntity<ApiResponse<NotificationUnreadCountResponse>> getUnreadCount(
            Authentication authentication) {
        NotificationUnreadCountResponse response = NotificationUnreadCountResponse.from(
                notificationFacade.getUnreadCount(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 로그인한 사용자가 본인 알림 한 건을 읽음 처리하는 API. 이미 읽은 알림의 재요청도 최초 읽음 시각으로 성공한다 */
    @PutMapping("/me/notifications/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markRead(
            Authentication authentication, @PathVariable @Positive Long notificationId) {
        NotificationReadResponse response = NotificationReadResponse.from(
                notificationFacade.markRead(authentication.getName(), notificationId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 로그인한 사용자가 본인의 미읽음 알림을 모두 읽음 처리하는 API. 대상이 없어도 변경 수 0으로 성공한다 */
    @PutMapping("/me/notifications/read")
    public ResponseEntity<ApiResponse<NotificationReadAllResponse>> markAllRead(Authentication authentication) {
        NotificationReadAllResponse response = NotificationReadAllResponse.from(
                notificationFacade.markAllRead(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
