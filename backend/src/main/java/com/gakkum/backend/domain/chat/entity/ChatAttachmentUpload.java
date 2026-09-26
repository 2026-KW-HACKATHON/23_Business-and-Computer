package com.gakkum.backend.domain.chat.entity;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_attachment_uploads")
public class ChatAttachmentUpload {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false, length = 26)
    private String roomId;

    @Column(name = "uploader_user_id", nullable = false, length = 26)
    private String uploaderUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatMessageType type;

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatAttachmentUploadStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 원래 파일명은 저장소 키에 넣지 않고 확장자만 사용한다. */
    public static ChatAttachmentUpload create(String roomId, String uploaderUserId, ChatMessageType type,
            String fileName, String contentType, long fileSize, LocalDateTime expiresAt) {
        ChatAttachmentUpload upload = new ChatAttachmentUpload();
        upload.id = UUID.randomUUID();
        upload.roomId = roomId;
        upload.uploaderUserId = uploaderUserId;
        upload.type = type;
        String extension = extensionOf(fileName);
        upload.storageKey = "chat/" + roomId + "/" + upload.id + (extension.isEmpty() ? "" : "." + extension);
        upload.fileName = fileName;
        upload.contentType = contentType;
        upload.fileSize = fileSize;
        upload.status = ChatAttachmentUploadStatus.PENDING;
        upload.expiresAt = expiresAt;
        return upload;
    }

    /** 점을 제외한 소문자 확장자를 반환한다. 확장자가 없으면 빈 문자열이다. */
    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
