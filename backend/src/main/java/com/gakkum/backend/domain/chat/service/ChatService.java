package com.gakkum.backend.domain.chat.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.gakkum.backend.domain.chat.client.ChatAttachmentStorageClient;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendMessageResult;
import com.gakkum.backend.domain.chat.entity.ChatAttachmentUpload;
import com.gakkum.backend.domain.chat.entity.ChatAttachmentUploadStatus;
import com.gakkum.backend.domain.chat.entity.ChatMessage;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.chat.repository.ChatAttachmentUploadRepository;
import com.gakkum.backend.domain.chat.repository.ChatMessageRepository;
import com.gakkum.backend.domain.chat.repository.ChatRoomRepository;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAttachmentUploadRepository chatAttachmentUploadRepository;
    private final ChatAttachmentPolicy chatAttachmentPolicy;
    private final ChatAttachmentStorageClient chatAttachmentStorageClient;
    private final Clock clock;

    public List<ChatRoom> findRoomsByJobIds(List<Long> jobIds) {
        return chatRoomRepository.findByJobIdIn(jobIds);
    }

    public Map<String, ChatMessage> findLatestMessages(List<String> roomIds) {
        return chatMessageRepository.findLatestByRoomIds(roomIds).stream()
                .collect(Collectors.toMap(ChatMessage::getRoomId, Function.identity()));
    }

    public Map<String, Long> countUnreadMessages(List<String> roomIds, String viewerId, boolean owner) {
        return chatMessageRepository.countUnreadByRoomIds(roomIds, viewerId, owner).stream()
                .collect(Collectors.toMap(ChatMessageRepository.UnreadCount::getRoomId,
                        ChatMessageRepository.UnreadCount::getUnreadCount));
    }

    public ChatRoom findLockedRoom(String roomId) {
        return chatRoomRepository.findLockedById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    public ChatRoom findRoom(String roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    public List<ChatMessage> findMessages(String roomId) {
        return chatMessageRepository.findByRoomIdOrderByIdAsc(roomId);
    }

    public void markRead(ChatRoom room, UserRole role, Long lastReadMessageId) {
        ChatMessage message = chatMessageRepository.findById(lastReadMessageId)
                .filter(found -> room.getId().equals(found.getRoomId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        room.markRead(role, message.getId());
    }

    public SendMessageResult sendTextMessage(ChatRoom room, String senderUserId, UUID clientMessageId, String content) {
        ChatMessage existing = chatMessageRepository.findByRoomIdAndSenderUserIdAndClientMessageId(
                room.getId(), senderUserId, clientMessageId).orElse(null);
        if (existing != null) {
            if (existing.getType() != ChatMessageType.TEXT || !content.equals(existing.getContent())) {
                throw new BusinessException(ErrorCode.CHAT_MESSAGE_CONFLICT);
            }
            return SendMessageResult.of(existing, false);
        }

        ChatMessage message = chatMessageRepository.saveAndFlush(ChatMessage.createText(
                room.getId(), senderUserId, clientMessageId, content));
        return SendMessageResult.of(message, true);
    }

    public ChatAttachmentUpload createAttachmentUpload(ChatRoom room, String uploaderUserId, ChatMessageType type,
            String fileName, String contentType, long fileSize) {
        String validContentType = chatAttachmentPolicy.validate(type, fileName, contentType, fileSize);
        LocalDateTime expiresAt = now().plus(chatAttachmentPolicy.getUploadTtl());
        return chatAttachmentUploadRepository.save(ChatAttachmentUpload.create(
                room.getId(), uploaderUserId, type, fileName, validContentType, fileSize, expiresAt));
    }

    public SendMessageResult sendAttachmentMessage(ChatRoom room, String senderUserId, UUID clientMessageId,
            ChatMessageType type, UUID uploadId) {
        // 재시도 판정을 업로드 검증보다 먼저 해야 이미 이 메시지에 연결된 업로드로 거부되지 않는다
        ChatMessage existing = chatMessageRepository.findByRoomIdAndSenderUserIdAndClientMessageId(
                room.getId(), senderUserId, clientMessageId).orElse(null);
        if (existing != null) {
            if (existing.getType() != type || !uploadId.equals(existing.getAttachmentUploadId())) {
                throw new BusinessException(ErrorCode.CHAT_MESSAGE_CONFLICT);
            }
            return SendMessageResult.of(existing, false);
        }

        // 다른 사용자나 다른 방의 업로드는 존재 여부를 드러내지 않도록 없는 업로드로 처리한다
        ChatAttachmentUpload upload = chatAttachmentUploadRepository.findById(uploadId)
                .filter(found -> found.getRoomId().equals(room.getId())
                        && found.getUploaderUserId().equals(senderUserId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_UPLOAD_NOT_FOUND));
        if (upload.getType() != type) {
            throw new BusinessException(ErrorCode.CHAT_UPLOAD_TYPE_NOT_ALLOWED);
        }
        if (upload.getStatus() == ChatAttachmentUploadStatus.ATTACHED) {
            throw new BusinessException(ErrorCode.CHAT_UPLOAD_ALREADY_USED);
        }
        if (upload.isExpired(now())) {
            throw new BusinessException(ErrorCode.CHAT_UPLOAD_NOT_READY);
        }
        boolean uploaded = chatAttachmentStorageClient.findObject(upload.getStorageKey())
                .filter(object -> object.size() == upload.getFileSize())
                .isPresent();
        if (!uploaded) {
            throw new BusinessException(ErrorCode.CHAT_UPLOAD_NOT_READY);
        }

        // 태그를 먼저 바꿔야 저장이 실패해도 전송된 첨부가 수명 주기 규칙으로 삭제되지 않는다
        chatAttachmentStorageClient.markAttached(upload.getStorageKey());
        upload.attach();
        ChatMessage message = chatMessageRepository.saveAndFlush(
                ChatMessage.createAttachment(senderUserId, clientMessageId, upload));
        return SendMessageResult.of(message, true);
    }

    // createdAt과 같은 JVM 기본 시간대를 써서 업로드 만료 비교 기준을 맞춘다
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
    }
}
