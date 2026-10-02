package com.gakkum.backend.application.media.dto;

import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.media.dto.MediaCommandDto.PrepareImageUploadCommand;

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
public class PrepareImageUploadRequest {

    @NotNull
    private ImagePurpose purpose;

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

    public static PrepareImageUploadRequest of(ImagePurpose purpose, String fileName, String contentType,
            Long size) {
        return PrepareImageUploadRequest.builder()
                .purpose(purpose)
                .fileName(fileName)
                .contentType(contentType)
                .size(size)
                .build();
    }

    public PrepareImageUploadCommand toCommand(String username) {
        return PrepareImageUploadCommand.of(username, purpose, fileName, contentType, size);
    }
}
