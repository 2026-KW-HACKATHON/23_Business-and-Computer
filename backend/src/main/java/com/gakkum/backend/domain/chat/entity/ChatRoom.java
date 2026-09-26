package com.gakkum.backend.domain.chat.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.gakkum.backend.util.UlidGenerator;
import com.gakkum.backend.domain.user.entity.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_rooms", uniqueConstraints = @UniqueConstraint(
        name = "chat_rooms_job_id_key", columnNames = "job_id"))
public class ChatRoom {

    @Id
    @Column(length = 26, nullable = false, updatable = false)
    private String id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "owner_last_read_message_id")
    private Long ownerLastReadMessageId;

    @Column(name = "student_last_read_message_id")
    private Long studentLastReadMessageId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ChatRoom create(Long jobId) {
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.id = UlidGenerator.generate();
        chatRoom.jobId = jobId;
        return chatRoom;
    }

    public void markRead(UserRole role, Long messageId) {
        if (role == UserRole.OWNER && (ownerLastReadMessageId == null || messageId > ownerLastReadMessageId)) {
            ownerLastReadMessageId = messageId;
        } else if (role == UserRole.STUDENT
                && (studentLastReadMessageId == null || messageId > studentLastReadMessageId)) {
            studentLastReadMessageId = messageId;
        }
    }
}
