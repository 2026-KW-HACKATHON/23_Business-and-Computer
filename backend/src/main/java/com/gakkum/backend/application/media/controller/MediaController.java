package com.gakkum.backend.application.media.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.media.dto.PrepareImageUploadRequest;
import com.gakkum.backend.application.media.dto.PrepareImageUploadResponse;
import com.gakkum.backend.application.media.facade.MediaFacade;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MediaController {

    private final MediaFacade mediaFacade;

    /** 프로필·매장 사진 업로드 준비 API(PresignedURL과 공개 이미지 URL 반환) */
    @PostMapping("/media/images/uploads")
    public ResponseEntity<ApiResponse<PrepareImageUploadResponse>> prepareImageUpload(
            Authentication authentication,
            @Valid @RequestBody PrepareImageUploadRequest request) {
        PrepareImageUploadResponse response = PrepareImageUploadResponse.from(
                mediaFacade.prepareImageUpload(request.toCommand(authentication.getName())));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
