package com.gakkum.backend.domain.chat.dto;

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
}
