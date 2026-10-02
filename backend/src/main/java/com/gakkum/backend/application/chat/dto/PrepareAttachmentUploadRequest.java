package com.gakkum.backend.application.chat.dto;

import com.gakkum.backend.domain.chat.dto.ChatCommandDto.PrepareAttachmentUploadCommand;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
public class PrepareAttachmentUploadRequest {

    @NotNull
    private ChatMessageType type;

    // 경로 구분자와 제어 문자는 다운로드 파일명에 쓸 수 없어 거부한다
    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[^/\\\\\\p{Cntrl}]+")
    private String fileName;

    @NotBlank
    @Size(max = 100)
    private String contentType;

    @NotNull
    @Positive
    private Long size;

    public static PrepareAttachmentUploadRequest of(ChatMessageType type, String fileName, String contentType,
            Long size) {
        return PrepareAttachmentUploadRequest.builder()
                .type(type)
                .fileName(fileName)
                .contentType(contentType)
                .size(size)
                .build();
    }

    @AssertTrue
    private boolean isAttachmentType() {
        return type != ChatMessageType.TEXT;
    }

    public PrepareAttachmentUploadCommand toCommand(String username, String roomId) {
        return PrepareAttachmentUploadCommand.of(username, roomId, type, fileName, contentType, size);
    }
}
