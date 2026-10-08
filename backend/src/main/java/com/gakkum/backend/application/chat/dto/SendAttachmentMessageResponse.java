package com.gakkum.backend.application.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendAttachmentMessageResult;
import com.gakkum.backend.domain.chat.entity.ChatMessage;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.global.response.KoreaTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SendAttachmentMessageResponse {

    private final Long id;
    private final String roomId;
    private final UUID clientMessageId;
    private final String senderUserId;
    private final ChatMessageType type;
    /** IMAGE·FILE의 단기 열람 URL */
    private final String content;
    private final String attachmentName;
    private final OffsetDateTime contentExpiresAt;
    private final OffsetDateTime createdAt;

    public static SendAttachmentMessageResponse from(SendAttachmentMessageResult result) {
        ChatMessage message = result.getMessage();
        return SendAttachmentMessageResponse.builder()
                .id(message.getId())
                .roomId(message.getRoomId())
                .clientMessageId(message.getClientMessageId())
                .senderUserId(message.getSenderUserId())
                .type(message.getType())
                .content(result.getContentUrl())
                .attachmentName(message.getAttachmentName())
                .contentExpiresAt(KoreaTime.from(result.getContentExpiresAt()))
                .createdAt(KoreaTime.from(message.getCreatedAt()))
                .build();
    }
}
