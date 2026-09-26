package com.gakkum.backend.domain.chat.service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import com.gakkum.backend.domain.chat.entity.ChatAttachmentUpload;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

/**
 * 첨부 파일의 허용 형식과 크기, 업로드 기록 유효 기간을 정한다.
 * 확장자와 MIME 짝은 설정 실수로 검증이 풀리지 않도록 코드에서 관리한다.
 */
@Component
public class ChatAttachmentPolicy {

    private static final Map<ChatMessageType, Map<String, Set<String>>> CONTENT_TYPES = Map.of(
            ChatMessageType.IMAGE, Map.of(
                    "jpg", Set.of("image/jpeg"),
                    "jpeg", Set.of("image/jpeg"),
                    "png", Set.of("image/png"),
                    "webp", Set.of("image/webp"),
                    "gif", Set.of("image/gif")),
            ChatMessageType.FILE, Map.of(
                    "pdf", Set.of("application/pdf"),
                    // Windows 브라우저는 zip을 application/x-zip-compressed로 보낸다
                    "zip", Set.of("application/zip", "application/x-zip-compressed"),
                    "doc", Set.of("application/msword"),
                    "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                    "xls", Set.of("application/vnd.ms-excel"),
                    "xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    "ppt", Set.of("application/vnd.ms-powerpoint"),
                    "pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")));

    private final Map<ChatMessageType, DataSize> maxSizes;
    private final Duration uploadTtl;

    public ChatAttachmentPolicy(
            @Value("${chat-attachment.image-max-size}") DataSize imageMaxSize,
            @Value("${chat-attachment.file-max-size}") DataSize fileMaxSize,
            @Value("${chat-attachment.upload-ttl}") Duration uploadTtl) {
        this.maxSizes = Map.of(ChatMessageType.IMAGE, imageMaxSize, ChatMessageType.FILE, fileMaxSize);
        this.uploadTtl = uploadTtl;
    }

    /** 형식과 크기를 검증하고 저장·서명에 쓸 소문자 contentType을 반환한다. */
    public String validate(ChatMessageType type, String fileName, String contentType, long size) {
        Set<String> allowed = CONTENT_TYPES.getOrDefault(type, Map.of())
                .get(ChatAttachmentUpload.extensionOf(fileName));
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        if (allowed == null || !allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.CHAT_UPLOAD_TYPE_NOT_ALLOWED);
        }
        if (size > maxSizes.get(type).toBytes()) {
            throw new BusinessException(ErrorCode.CHAT_UPLOAD_TOO_LARGE);
        }
        return normalized;
    }

    public Duration getUploadTtl() {
        return uploadTtl;
    }
}
