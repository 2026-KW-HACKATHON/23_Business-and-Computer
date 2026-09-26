package com.gakkum.backend.application.chat.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.gakkum.backend.domain.chat.entity.ChatMessage;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessageListResponse {

    private final List<Message> messages;

    public static ChatMessageListResponse from(List<ChatMessage> messages) {
        return new ChatMessageListResponse(messages.stream().map(Message::from).toList());
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Message {
        private final Long id;
        private final String roomId;
        private final UUID clientMessageId;
        private final String senderUserId;
        private final ChatMessageType type;
        private final String content;
        private final String attachmentName;
        /** IMAGE·FILE 열람 URL의 만료 시각. TEXT는 null */
        private final LocalDateTime contentExpiresAt;
        private final LocalDateTime createdAt;

        public static Message from(ChatMessage message) {
            return Message.builder()
                    .id(message.getId())
                    .roomId(message.getRoomId())
                    .clientMessageId(message.getClientMessageId())
                    .senderUserId(message.getSenderUserId())
                    .type(message.getType())
                    .content(message.getContent())
                    .attachmentName(message.getAttachmentName())
                    .createdAt(message.getCreatedAt())
                    .build();
        }
    }
}
