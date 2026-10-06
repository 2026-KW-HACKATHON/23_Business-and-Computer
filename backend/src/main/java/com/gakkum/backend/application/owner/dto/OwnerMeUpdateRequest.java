package com.gakkum.backend.application.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.UpdateOwnerMeCommand;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OwnerMeUpdateRequest {

    @NotBlank
    @Size(max = 255)
    private String storeName;

    @NotNull
    @Positive
    private Long categoryId;

    @Size(max = 255)
    @Pattern(regexp = "^$|^https?://\\S+$")
    private String profileImageUrl;

    @Size(max = 255)
    private String storeAddress;

    private String description;

    public static OwnerMeUpdateRequest of(
            String storeName,
            Long categoryId,
            String profileImageUrl,
            String storeAddress,
            String description) {
        return OwnerMeUpdateRequest.builder()
                .storeName(storeName)
                .categoryId(categoryId)
                .profileImageUrl(profileImageUrl)
                .storeAddress(storeAddress)
                .description(description)
                .build();
    }

    public UpdateOwnerMeCommand toCommand(String username) {
        return UpdateOwnerMeCommand.of(
                username,
                storeName.trim(),
                categoryId,
                normalizeOptional(profileImageUrl),
                normalizeOptional(storeAddress),
                normalizeOptional(description));
    }

    // 생략·null·빈 문자열·공백만 있는 값은 모두 삭제(null)로 본다
    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
