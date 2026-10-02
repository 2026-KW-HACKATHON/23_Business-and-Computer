package com.gakkum.backend.application.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.MarkReadCommand;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReadChatRoomRequest {

    @NotNull
    @Positive
    private Long lastReadMessageId;

    public static ReadChatRoomRequest of(Long lastReadMessageId) {
        return ReadChatRoomRequest.builder().lastReadMessageId(lastReadMessageId).build();
    }

    public MarkReadCommand toCommand(String username, String roomId) {
        return MarkReadCommand.of(username, roomId, lastReadMessageId);
    }
}
