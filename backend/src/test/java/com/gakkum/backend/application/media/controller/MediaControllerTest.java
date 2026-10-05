package com.gakkum.backend.application.media.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.media.facade.MediaFacade;
import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.media.dto.MediaCommandDto.PrepareImageUploadCommand;
import com.gakkum.backend.domain.media.dto.MediaQueryDto.PrepareImageUploadResult;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

class MediaControllerTest {

    private static final String VALID_BODY =
            "{\"purpose\":\"STORE\",\"fileName\":\"store.png\",\"contentType\":\"image/png\",\"size\":482133}";

    private final MediaFacade facade = mock(MediaFacade.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("KAKAO_123", null);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MediaController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("사진 업로드 준비는 201과 업로드 URL, 필수 헤더, 만료 시각, 공개 이미지 URL을 반환한다")
    void returnsCreatedUpload() throws Exception {
        when(facade.prepareImageUpload(any())).thenReturn(PrepareImageUploadResult.of(
                "https://bucket.s3.ap-northeast-2.amazonaws.com/images/store/u/1.png?X-Amz-Signature=abc",
                Map.of("content-type", "image/png"),
                LocalDateTime.of(2026, 9, 27, 12, 10),
                "https://bucket.s3.ap-northeast-2.amazonaws.com/images/store/u/1.png"));

        mockMvc.perform(post("/media/images/uploads").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadUrl").value(
                        "https://bucket.s3.ap-northeast-2.amazonaws.com/images/store/u/1.png?X-Amz-Signature=abc"))
                .andExpect(jsonPath("$.data.uploadHeaders.content-type").value("image/png"))
                .andExpect(jsonPath("$.data.uploadUrlExpiresAt").value("2026-09-27T12:10:00"))
                .andExpect(jsonPath("$.data.imageUrl").value(
                        "https://bucket.s3.ap-northeast-2.amazonaws.com/images/store/u/1.png"));

        ArgumentCaptor<PrepareImageUploadCommand> captor = ArgumentCaptor.forClass(PrepareImageUploadCommand.class);
        verify(facade).prepareImageUpload(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("KAKAO_123");
        assertThat(captor.getValue().getPurpose()).isEqualTo(ImagePurpose.STORE);
        assertThat(captor.getValue().getFileName()).isEqualTo("store.png");
        assertThat(captor.getValue().getContentType()).isEqualTo("image/png");
        assertThat(captor.getValue().getSize()).isEqualTo(482133L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STORE", "JOB"})
    @DisplayName("매장과 의뢰용 업로드 요청의 용도를 서비스에 전달한다")
    void acceptsImagePurpose(String purpose) throws Exception {
        when(facade.prepareImageUpload(any())).thenReturn(PrepareImageUploadResult.of(
                "https://upload.example.com", Map.of("content-type", "image/png"),
                LocalDateTime.of(2026, 10, 5, 12, 0), "https://images.example.com/image.png"));

        mockMvc.perform(post("/media/images/uploads").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("STORE", purpose)))
                .andExpect(status().isCreated());

        ArgumentCaptor<PrepareImageUploadCommand> captor = ArgumentCaptor.forClass(PrepareImageUploadCommand.class);
        verify(facade).prepareImageUpload(captor.capture());
        assertThat(captor.getValue().getPurpose()).isEqualTo(ImagePurpose.valueOf(purpose));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"purpose\":\"CHAT\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"size\":1}",
            "{\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"size\":1}",
            "{\"purpose\":\"PROFILE\",\"fileName\":\"../a.png\",\"contentType\":\"image/png\",\"size\":1}",
            "{\"purpose\":\"PROFILE\",\"fileName\":\"a.png\",\"contentType\":\" \",\"size\":1}",
            "{\"purpose\":\"PROFILE\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"size\":0}",
            "{\"purpose\":\"PROFILE\",\"fileName\":\"a.png\",\"contentType\":\"image/png\"}"})
    @DisplayName("용도가 잘못되거나 필수 값이 비었거나 크기가 양수가 아니면 COMMON_400을 반환한다")
    void rejectsInvalidRequest(String body) throws Exception {
        mockMvc.perform(post("/media/images/uploads").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(facade);
    }

    @Test
    @DisplayName("허용되지 않는 형식과 초과 크기는 각각 MEDIA_UPLOAD_400 코드로 반환한다")
    void returnsPolicyErrors() throws Exception {
        when(facade.prepareImageUpload(any()))
                .thenThrow(new BusinessException(ErrorCode.MEDIA_UPLOAD_TYPE_NOT_ALLOWED))
                .thenThrow(new BusinessException(ErrorCode.MEDIA_UPLOAD_TOO_LARGE));

        mockMvc.perform(post("/media/images/uploads").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MEDIA_UPLOAD_400_TYPE"));
        mockMvc.perform(post("/media/images/uploads").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MEDIA_UPLOAD_400_SIZE"));
    }

    @Test
    @DisplayName("저장소 오류는 502와 MEDIA_UPLOAD_502를 반환한다")
    void returnsBadGatewayWhenStorageUnavailable() throws Exception {
        when(facade.prepareImageUpload(any()))
                .thenThrow(new BusinessException(ErrorCode.MEDIA_UPLOAD_UNAVAILABLE));

        mockMvc.perform(post("/media/images/uploads").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("MEDIA_UPLOAD_502"));
    }
}
