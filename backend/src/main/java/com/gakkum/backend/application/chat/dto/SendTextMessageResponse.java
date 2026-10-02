package com.gakkum.backend.application.chat.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendMessageResult;
import com.gakkum.backend.domain.chat.entity.ChatMessage;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SendTextMessageResponse {

    private final Long id;
    private final String roomId;
    private final UUID clientMessageId;
    private final String senderUserId;
    private final ChatMessageType type;
    private final String content;
    private final LocalDateTime createdAt;

    public static SendTextMessageResponse from(SendMessageResult result) {
        ChatMessage message = result.getMessage();
        return SendTextMessageResponse.builder()
                .id(message.getId())
                .roomId(message.getRoomId())
                .clientMessageId(message.getClientMessageId())
                .senderUserId(message.getSenderUserId())
                .type(message.getType())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
