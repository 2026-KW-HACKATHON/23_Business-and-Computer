package com.gakkum.backend.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;

import com.gakkum.backend.domain.media.client.MediaImageStorageClient;
import com.gakkum.backend.domain.media.client.MediaImageStorageClient.PresignedImageUpload;
import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.media.dto.MediaQueryDto.PrepareImageUploadResult;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class MediaServiceTest {

    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-27T03:10:00Z");

    private final MediaImageStorageClient storageClient = mock(MediaImageStorageClient.class);
    private final MediaService service = new MediaService(storageClient, DataSize.ofMegabytes(10));

    @BeforeEach
    void setUp() {
        when(storageClient.presignUpload(anyString(), anyString(), anyLong()))
                .thenAnswer(invocation -> new PresignedImageUpload("https://upload.example.com",
                        Map.of("content-type", invocation.getArgument(1)), EXPIRES_AT,
                        "https://images.example.com/" + invocation.getArgument(0)));
    }

    @Test
    @DisplayName("사용자 ID와 임의 ID로 용도별 키를 만들고 업로드 URL과 공개 이미지 URL을 반환한다")
    void preparesUploadWithUserScopedKey() {
        PrepareImageUploadResult result = service.prepareImageUpload(
                USER_ID, ImagePurpose.STORE, "매장 전경.JPG", " Image/JPEG ", 482133);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageClient).presignUpload(keyCaptor.capture(), eq("image/jpeg"), eq(482133L));
        assertThat(keyCaptor.getValue())
                .matches("images/store/" + USER_ID + "/[0-9a-f-]{36}\\.jpg");
        assertThat(result.getUploadUrl()).isEqualTo("https://upload.example.com");
        assertThat(result.getUploadHeaders()).isEqualTo(Map.of("content-type", "image/jpeg"));
        assertThat(result.getUploadUrlExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(EXPIRES_AT, ZoneId.systemDefault()));
        assertThat(result.getImageUrl()).isEqualTo("https://images.example.com/" + keyCaptor.getValue());
    }

    @Test
    @DisplayName("프로필 사진은 profile 경로에 저장하고 호출마다 다른 키를 만든다")
    void usesProfilePrefixAndUniqueKeys() {
        PrepareImageUploadResult first = service.prepareImageUpload(
                USER_ID, ImagePurpose.PROFILE, "me.webp", "image/webp", 100);
        PrepareImageUploadResult second = service.prepareImageUpload(
                USER_ID, ImagePurpose.PROFILE, "me.webp", "image/webp", 100);

        assertThat(first.getImageUrl()).startsWith("https://images.example.com/images/profile/" + USER_ID + "/");
        assertThat(first.getImageUrl()).isNotEqualTo(second.getImageUrl());
    }

    @ParameterizedTest
    @CsvSource({
            "photo.gif, image/gif",
            "photo.png, image/jpeg",
            "photo.jpg, image/png",
            "photo, image/png",
            "document.pdf, application/pdf",
            "photo.svg, image/svg+xml"})
    @DisplayName("JPEG·PNG·WebP가 아니거나 확장자와 형식이 맞지 않으면 MEDIA_UPLOAD_400_TYPE으로 거부한다")
    void rejectsDisallowedType(String fileName, String contentType) {
        assertThatThrownBy(() -> service.prepareImageUpload(USER_ID, ImagePurpose.PROFILE, fileName, contentType, 100))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_TYPE_NOT_ALLOWED));
        verifyNoInteractions(storageClient);
    }

    @Test
    @DisplayName("10MB는 허용하고 10MB를 넘으면 MEDIA_UPLOAD_400_SIZE로 거부한다")
    void enforcesMaxSize() {
        long max = DataSize.ofMegabytes(10).toBytes();

        service.prepareImageUpload(USER_ID, ImagePurpose.STORE, "store.png", "image/png", max);

        assertThatThrownBy(() -> service.prepareImageUpload(
                USER_ID, ImagePurpose.STORE, "store.png", "image/png", max + 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA_UPLOAD_TOO_LARGE));
    }
}
