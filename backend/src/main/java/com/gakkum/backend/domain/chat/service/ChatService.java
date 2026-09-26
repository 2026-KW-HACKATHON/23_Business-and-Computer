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

import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendMessageResult;
import com.gakkum.backend.domain.chat.entity.ChatAttachmentUpload;
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
        // createdAt과 같은 JVM 기본 시간대로 저장해 만료 비교 기준을 맞춘다
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                clock.instant().plus(chatAttachmentPolicy.getUploadTtl()), ZoneId.systemDefault());
        return chatAttachmentUploadRepository.save(ChatAttachmentUpload.create(
                room.getId(), uploaderUserId, type, fileName, validContentType, fileSize, expiresAt));
    }
}
