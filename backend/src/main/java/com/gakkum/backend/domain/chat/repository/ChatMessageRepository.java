package com.gakkum.backend.domain.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gakkum.backend.domain.chat.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 방별 최신 메시지는 파생 쿼리로 한 번에 조회할 수 없어 PostgreSQL DISTINCT ON을 사용한다.
    @Query(value = """
            SELECT DISTINCT ON (room_id) * FROM chat_messages
            WHERE room_id IN (:roomIds)
            ORDER BY room_id, id DESC
            """, nativeQuery = true)
    List<ChatMessage> findLatestByRoomIds(@Param("roomIds") List<String> roomIds);

    // 방마다 읽음 위치가 달라서 방별 집계를 한 번의 쿼리로 계산한다.
    @Query(value = """
            SELECT m.room_id AS "roomId", COUNT(*) AS "unreadCount"
            FROM chat_messages m
            JOIN chat_rooms r ON r.id = m.room_id
            WHERE m.room_id IN (:roomIds)
              AND m.sender_user_id <> :viewerUserId
              AND m.id > COALESCE(
                  CASE WHEN :ownerView THEN r.owner_last_read_message_id
                       ELSE r.student_last_read_message_id END, 0)
            GROUP BY m.room_id
            """, nativeQuery = true)
    List<UnreadCount> countUnreadByRoomIds(
            @Param("roomIds") List<String> roomIds,
            @Param("viewerUserId") String viewerUserId,
            @Param("ownerView") boolean ownerView);

    interface UnreadCount {
        String getRoomId();
        Long getUnreadCount();
    }
}
