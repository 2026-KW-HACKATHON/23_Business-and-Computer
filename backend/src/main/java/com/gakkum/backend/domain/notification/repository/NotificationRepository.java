package com.gakkum.backend.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gakkum.backend.domain.notification.entity.Notification;

import jakarta.persistence.LockModeType;

/** 모든 조회·변경은 수신자 조건을 포함해 본인 알림만 다룬다. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 수신자의 알림 첫 페이지를 최신순으로 읽는다. */
    List<Notification> findByRecipientUserIdOrderByCreatedAtDescIdDesc(String recipientUserId, Limit limit);

    /*
     * 다음 페이지의 경계는 정렬 키 튜플 (createdAt, id)의 대소 비교라 메서드 이름으로 표현할 수 없다.
     * 커서가 가리키는 알림보다 뒤(더 오래되었거나 같은 시각의 더 작은 ID)의 알림만 최신순으로 읽는다.
     */
    @Query("""
            select n from Notification n
            where n.recipientUserId = :recipientUserId
              and (n.createdAt, n.id) < (:createdAt, :id)
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findPageAfterCursor(@Param("recipientUserId") String recipientUserId,
            @Param("createdAt") LocalDateTime createdAt, @Param("id") Long id, Limit limit);

    long countByRecipientUserIdAndReadAtIsNull(String recipientUserId);

    /** 같은 알림의 읽음 요청을 순서대로 처리하도록 본인 알림 행을 잠근다. 다른 수신자의 알림은 찾지 않는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findLockedByIdAndRecipientUserId(Long id, String recipientUserId);

    /*
     * 수신자의 미읽음 알림 전체를 UPDATE 한 번으로 읽음 처리해야 해서 메서드 이름으로 표현할 수 없다.
     * 읽음 시각이 비어 있는 행만 바꾸므로 먼저 기록된 읽음 시각은 덮어쓰지 않는다. 바꾼 행 수를 반환한다.
     */
    @Modifying
    @Query("""
            update Notification n set n.readAt = :readAt
            where n.recipientUserId = :recipientUserId and n.readAt is null
            """)
    int markAllRead(@Param("recipientUserId") String recipientUserId, @Param("readAt") LocalDateTime readAt);
}
