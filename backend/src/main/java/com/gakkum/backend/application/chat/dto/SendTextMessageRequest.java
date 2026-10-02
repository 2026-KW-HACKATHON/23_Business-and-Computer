package com.gakkum.backend.application.chat.dto;

import java.util.UUID;

import com.gakkum.backend.domain.chat.dto.ChatCommandDto.SendTextMessageCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SendTextMessageRequest {

    @NotNull
    private UUID clientMessageId;

    @NotBlank
    @Size(max = 5000)
    private String content;

    public static SendTextMessageRequest of(UUID clientMessageId, String content) {
        return SendTextMessageRequest.builder()
                .clientMessageId(clientMessageId)
                .content(content)
                .build();
    }

    public SendTextMessageCommand toCommand(String username, String roomId) {
        return SendTextMessageCommand.of(username, roomId, clientMessageId, content);
    }
}
