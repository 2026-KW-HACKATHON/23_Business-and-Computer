package com.gakkum.backend.application.chat.dto;

import java.util.UUID;

import com.gakkum.backend.domain.chat.dto.ChatCommandDto.SendAttachmentMessageCommand;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SendAttachmentMessageRequest {

    @NotNull
    private UUID clientMessageId;

    @NotNull
    private ChatMessageType type;

    @NotNull
    private UUID uploadId;

    public static SendAttachmentMessageRequest of(UUID clientMessageId, ChatMessageType type, UUID uploadId) {
        return SendAttachmentMessageRequest.builder()
                .clientMessageId(clientMessageId)
                .type(type)
                .uploadId(uploadId)
                .build();
    }

    @AssertTrue
    private boolean isAttachmentType() {
        return type != ChatMessageType.TEXT;
    }

    public SendAttachmentMessageCommand toCommand(String username, String roomId) {
        return SendAttachmentMessageCommand.of(username, roomId, clientMessageId, type, uploadId);
    }
}
