package com.gakkum.backend.domain.chat.dto;

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
}
