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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
                .isEqualTo(LocalDateTime.ofInstant(EXPIRES_AT, ZoneOffset.UTC));
        assertThat(result.getImageUrl()).isEqualTo("https://images.example.com/" + keyCaptor.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = { "UTC", "Asia/Seoul" })
    @DisplayName("업로드 URL 만료 시각은 JVM 기본 시간대와 무관하게 UTC 시각으로 넘긴다")
    void expiresAtIgnoresDefaultTimeZone(String defaultZone) {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(defaultZone));
        try {
            PrepareImageUploadResult result = service.prepareImageUpload(
                    USER_ID, ImagePurpose.STORE, "매장 전경.JPG", "image/jpeg", 482133);

            assertThat(result.getUploadUrlExpiresAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 3, 10));
        } finally {
            TimeZone.setDefault(original);
        }
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

    @Test
    @DisplayName("제안 사진은 proposal 경로에 저장한다")
    void usesProposalPrefix() {
        service.prepareImageUpload(USER_ID, ImagePurpose.PROPOSAL, "참고.png", "image/png", 100);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageClient).presignUpload(keyCaptor.capture(), eq("image/png"), eq(100L));
        assertThat(keyCaptor.getValue()).matches("images/proposal/" + USER_ID + "/[0-9a-f-]{36}\\.png");
    }

    @Test
    @DisplayName("의뢰 참고 사진은 작성자 ID 아래 job 경로에 업로드 URL을 발급한다")
    void usesJobPrefix() {
        service.prepareImageUpload(USER_ID, ImagePurpose.JOB, "참고.png", "image/png", 100);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageClient).presignUpload(keyCaptor.capture(), eq("image/png"), eq(100L));
        assertThat(keyCaptor.getValue()).matches("images/job/" + USER_ID + "/[0-9a-f-]{36}\\.png");
    }

    @Test
    @DisplayName("사용자·용도 경로 아래 UUID 파일명과 허용 확장자로 된 사진 URL만 저장소 키로 인정한다")
    void findsOnlyIssuedImageKeys() {
        String prefix = "images/proposal/" + USER_ID + "/";
        String uuid = "0b4f2a3e-6a8c-4a39-9f55-8f1d8f0b2c11";
        when(storageClient.findKey(anyString(), eq(prefix))).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            String base = "https://images.example.com/" + prefix;
            return url.startsWith(base) ? Optional.of(prefix + url.substring(base.length())) : Optional.empty();
        });

        assertThat(service.findImageKey(USER_ID, ImagePurpose.PROPOSAL,
                "https://images.example.com/" + prefix + uuid + ".webp")).contains(prefix + uuid + ".webp");
        assertThat(service.findImageKey(USER_ID, ImagePurpose.PROPOSAL,
                "https://images.example.com/" + prefix + uuid + ".gif")).isEmpty();
        assertThat(service.findImageKey(USER_ID, ImagePurpose.PROPOSAL,
                "https://images.example.com/" + prefix + uuid.toUpperCase() + ".png")).isEmpty();
        assertThat(service.findImageKey(USER_ID, ImagePurpose.PROPOSAL,
                "https://images.example.com/" + prefix + "photo.png")).isEmpty();
        assertThat(service.findImageKey(USER_ID, ImagePurpose.PROPOSAL,
                "https://images.example.com/" + prefix + uuid)).isEmpty();
        assertThat(service.findImageKey(USER_ID, ImagePurpose.PROPOSAL,
                "https://evil.example.com/" + prefix + uuid + ".png")).isEmpty();
    }

    @Test
    @DisplayName("사진 업로드 여부는 저장소 객체 존재 여부로 판단한다")
    void checksImageUploaded() {
        when(storageClient.exists("images/proposal/u/a.png")).thenReturn(true);

        assertThat(service.isImageUploaded("images/proposal/u/a.png")).isTrue();
        assertThat(service.isImageUploaded("images/proposal/u/b.png")).isFalse();
    }

    @Test
    @DisplayName("프로필·매장 사진 확인은 본인이 그 용도로 올린 사진이면 통과하고, 다른 사용자·다른 용도·외부 주소면 MEDIA_400_IMAGE_URL로 거부한다")
    void validatesImagesUploadedByUserForPurpose() {
        String uuid = "0b4f2a3e-6a8c-4a39-9f55-8f1d8f0b2c11";
        String own = "https://images.example.com/images/store/" + USER_ID + "/" + uuid + ".png";
        String otherUser = "https://images.example.com/images/store/OTHERUSER0000000000000001/" + uuid + ".png";
        String otherPurpose = "https://images.example.com/images/profile/" + USER_ID + "/" + uuid + ".png";
        givenPublicUrls();
        when(storageClient.exists("images/store/" + USER_ID + "/" + uuid + ".png")).thenReturn(true);

        service.validateUploadedImages(USER_ID, ImagePurpose.STORE, List.of(own));
        service.validateUploadedImages(USER_ID, ImagePurpose.STORE, List.of());

        for (String url : List.of(otherUser, otherPurpose, "https://evil.example.com/" + uuid + ".png")) {
            assertThatThrownBy(() -> service.validateUploadedImages(USER_ID, ImagePurpose.STORE, List.of(own, url)))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA_IMAGE_URL_INVALID));
        }
    }

    @Test
    @DisplayName("발급한 형태지만 아직 올리지 않은 사진이 있으면 MEDIA_409_IMAGE_NOT_UPLOADED로 거부한다")
    void rejectsImageNotYetUploaded() {
        String url = "https://images.example.com/images/profile/" + USER_ID
                + "/0b4f2a3e-6a8c-4a39-9f55-8f1d8f0b2c11.webp";
        givenPublicUrls();

        assertThatThrownBy(() -> service.validateUploadedImages(USER_ID, ImagePurpose.PROFILE, List.of(url)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA_IMAGE_NOT_UPLOADED));
    }

    // 실제 저장소 클라이언트처럼 공개 URL이 경로 접두사로 시작할 때만 키를 돌려준다
    private void givenPublicUrls() {
        when(storageClient.findKey(anyString(), anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            String base = "https://images.example.com/" + invocation.getArgument(1);
            return url.startsWith(base)
                    ? Optional.of(invocation.<String>getArgument(1) + url.substring(base.length()))
                    : Optional.empty();
        });
    }
}
