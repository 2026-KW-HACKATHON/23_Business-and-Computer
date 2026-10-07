package com.gakkum.backend.application.chat.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.gakkum.backend.domain.chat.dto.ChatQueryDto.MessageResult;
import com.gakkum.backend.domain.chat.entity.ChatMessage;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.global.response.KoreaTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessageListResponse {

    /** 조회한 사용자의 User.id. 메시지의 senderUserId와 비교해 내 메시지를 구분한다 */
    private final String viewerUserId;
    private final List<Message> messages;

    public static ChatMessageListResponse from(List<MessageResult> messages, String viewerUserId) {
        return new ChatMessageListResponse(viewerUserId, messages.stream().map(Message::from).toList());
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
        /** TEXT는 본문, IMAGE·FILE은 단기 열람 URL */
        private final String content;
        private final String attachmentName;
        /** IMAGE·FILE 열람 URL의 만료 시각. TEXT는 null */
        private final OffsetDateTime contentExpiresAt;
        private final OffsetDateTime createdAt;

        public static Message from(MessageResult result) {
            ChatMessage message = result.getMessage();
            return Message.builder()
                    .id(message.getId())
                    .roomId(message.getRoomId())
                    .clientMessageId(message.getClientMessageId())
                    .senderUserId(message.getSenderUserId())
                    .type(message.getType())
                    .content(result.getContent())
                    .attachmentName(message.getAttachmentName())
                    .contentExpiresAt(KoreaTime.from(result.getContentExpiresAt()))
                    .createdAt(KoreaTime.from(message.getCreatedAt()))
                    .build();
        }
    }
}
