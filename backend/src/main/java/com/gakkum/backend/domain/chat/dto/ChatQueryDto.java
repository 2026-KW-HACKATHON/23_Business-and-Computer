package com.gakkum.backend.domain.chat.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.gakkum.backend.domain.chat.entity.ChatMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class ChatQueryDto {

    private ChatQueryDto() {
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SendMessageResult {
        private final ChatMessage message;
        private final boolean created;

        public static SendMessageResult of(ChatMessage message, boolean created) {
            return new SendMessageResult(message, created);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PrepareAttachmentUploadResult {
        private final UUID uploadId;
        private final String uploadUrl;
        private final Map<String, String> uploadHeaders;
        private final LocalDateTime uploadUrlExpiresAt;

        public static PrepareAttachmentUploadResult of(UUID uploadId, String uploadUrl,
                Map<String, String> uploadHeaders, LocalDateTime uploadUrlExpiresAt) {
            return new PrepareAttachmentUploadResult(uploadId, uploadUrl, uploadHeaders, uploadUrlExpiresAt);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SendAttachmentMessageResult {
        private final ChatMessage message;
        private final boolean created;
        private final String contentUrl;
        private final LocalDateTime contentExpiresAt;

        public static SendAttachmentMessageResult of(ChatMessage message, boolean created, String contentUrl,
                LocalDateTime contentExpiresAt) {
            return new SendAttachmentMessageResult(message, created, contentUrl, contentExpiresAt);
        }
    }
}
