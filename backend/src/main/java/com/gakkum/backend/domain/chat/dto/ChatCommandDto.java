package com.gakkum.backend.domain.chat.dto;

import java.util.UUID;

import com.gakkum.backend.domain.chat.entity.ChatMessageType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class ChatCommandDto {

    private ChatCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MarkReadCommand {
        private final String username;
        private final String roomId;
        private final Long lastReadMessageId;

        public static MarkReadCommand of(String username, String roomId, Long lastReadMessageId) {
            return MarkReadCommand.builder()
                    .username(username)
                    .roomId(roomId)
                    .lastReadMessageId(lastReadMessageId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SendTextMessageCommand {
        private final String username;
        private final String roomId;
        private final UUID clientMessageId;
        private final String content;

        public static SendTextMessageCommand of(String username, String roomId, UUID clientMessageId, String content) {
            return SendTextMessageCommand.builder()
                    .username(username)
                    .roomId(roomId)
                    .clientMessageId(clientMessageId)
                    .content(content)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PrepareAttachmentUploadCommand {
        private final String username;
        private final String roomId;
        private final ChatMessageType type;
        private final String fileName;
        private final String contentType;
        private final long size;

        public static PrepareAttachmentUploadCommand of(String username, String roomId, ChatMessageType type,
                String fileName, String contentType, long size) {
            return PrepareAttachmentUploadCommand.builder()
                    .username(username)
                    .roomId(roomId)
                    .type(type)
                    .fileName(fileName)
                    .contentType(contentType)
                    .size(size)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SendAttachmentMessageCommand {
        private final String username;
        private final String roomId;
        private final UUID clientMessageId;
        private final ChatMessageType type;
        private final UUID uploadId;

        public static SendAttachmentMessageCommand of(String username, String roomId, UUID clientMessageId,
                ChatMessageType type, UUID uploadId) {
            return SendAttachmentMessageCommand.builder()
                    .username(username)
                    .roomId(roomId)
                    .clientMessageId(clientMessageId)
                    .type(type)
                    .uploadId(uploadId)
                    .build();
        }
    }
}
