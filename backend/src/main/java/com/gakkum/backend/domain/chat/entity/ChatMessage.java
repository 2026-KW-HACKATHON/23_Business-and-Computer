package com.gakkum.backend.domain.chat.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 26)
    private String roomId;

    @Column(name = "sender_user_id", nullable = false, length = 26)
    private String senderUserId;

    @Column(name = "client_message_id")
    private UUID clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatMessageType type;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "attachment_key", columnDefinition = "TEXT")
    private String attachmentKey;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @Column(name = "attachment_upload_id")
    private UUID attachmentUploadId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ChatMessage createText(String roomId, String senderUserId, UUID clientMessageId, String content) {
        return ChatMessage.builder()
                .roomId(roomId)
                .senderUserId(senderUserId)
                .clientMessageId(clientMessageId)
                .type(ChatMessageType.TEXT)
                .content(content)
                .build();
    }

    /** 열람 URL은 만료되므로 content에 저장하지 않고 응답을 만들 때 발급한다. */
    public static ChatMessage createAttachment(String senderUserId, UUID clientMessageId, ChatAttachmentUpload upload) {
        return ChatMessage.builder()
                .roomId(upload.getRoomId())
                .senderUserId(senderUserId)
                .clientMessageId(clientMessageId)
                .type(upload.getType())
                .attachmentKey(upload.getStorageKey())
                .attachmentName(upload.getFileName())
                .attachmentUploadId(upload.getId())
                .build();
    }
}
