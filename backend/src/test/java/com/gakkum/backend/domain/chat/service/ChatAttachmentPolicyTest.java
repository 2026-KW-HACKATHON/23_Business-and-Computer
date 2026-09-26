package com.gakkum.backend.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class ChatAttachmentPolicyTest {

    private final ChatAttachmentPolicy policy = new ChatAttachmentPolicy(
            DataSize.ofMegabytes(10), DataSize.ofMegabytes(50), Duration.ofHours(1));

    @Test
    @DisplayName("허용된 확장자와 형식은 대소문자와 관계없이 통과하고 소문자 형식을 반환한다")
    void acceptsAllowedFormatsCaseInsensitively() {
        assertThat(policy.validate(ChatMessageType.IMAGE, "시안.PNG", " Image/PNG ", 1024)).isEqualTo("image/png");
        assertThat(policy.validate(ChatMessageType.IMAGE, "사진.jpg", "image/jpeg", 1024)).isEqualTo("image/jpeg");
        assertThat(policy.validate(ChatMessageType.FILE, "견적서.pdf", "application/pdf", 1024))
                .isEqualTo("application/pdf");
        assertThat(policy.validate(ChatMessageType.FILE, "자료.zip", "application/x-zip-compressed", 1024))
                .isEqualTo("application/x-zip-compressed");
    }

    @Test
    @DisplayName("타입에 허용되지 않은 확장자나 확장자와 맞지 않는 형식은 CHAT_UPLOAD_400_TYPE으로 거부한다")
    void rejectsDisallowedFormats() {
        assertCode(ErrorCode.CHAT_UPLOAD_TYPE_NOT_ALLOWED,
                () -> policy.validate(ChatMessageType.IMAGE, "견적서.pdf", "application/pdf", 1024));
        assertCode(ErrorCode.CHAT_UPLOAD_TYPE_NOT_ALLOWED,
                () -> policy.validate(ChatMessageType.FILE, "사진.png", "image/png", 1024));
        assertCode(ErrorCode.CHAT_UPLOAD_TYPE_NOT_ALLOWED,
                () -> policy.validate(ChatMessageType.IMAGE, "시안.png", "image/jpeg", 1024));
        assertCode(ErrorCode.CHAT_UPLOAD_TYPE_NOT_ALLOWED,
                () -> policy.validate(ChatMessageType.FILE, "확장자없음", "application/pdf", 1024));
        assertCode(ErrorCode.CHAT_UPLOAD_TYPE_NOT_ALLOWED,
                () -> policy.validate(ChatMessageType.FILE, "실행.exe", "application/octet-stream", 1024));
    }

    @Test
    @DisplayName("타입별 최대 크기까지는 허용하고 넘으면 CHAT_UPLOAD_400_SIZE로 거부한다")
    void enforcesMaxSizePerType() {
        long imageMax = DataSize.ofMegabytes(10).toBytes();
        long fileMax = DataSize.ofMegabytes(50).toBytes();

        assertThat(policy.validate(ChatMessageType.IMAGE, "시안.png", "image/png", imageMax)).isEqualTo("image/png");
        assertThat(policy.validate(ChatMessageType.FILE, "견적서.pdf", "application/pdf", fileMax))
                .isEqualTo("application/pdf");
        assertCode(ErrorCode.CHAT_UPLOAD_TOO_LARGE,
                () -> policy.validate(ChatMessageType.IMAGE, "시안.png", "image/png", imageMax + 1));
        assertCode(ErrorCode.CHAT_UPLOAD_TOO_LARGE,
                () -> policy.validate(ChatMessageType.FILE, "견적서.pdf", "application/pdf", fileMax + 1));
    }

    private void assertCode(ErrorCode expected, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
