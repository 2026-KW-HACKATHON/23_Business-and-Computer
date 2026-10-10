package com.gakkum.backend.application.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.SaveStoreConcernCommand;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PUT /owners/me/concern 본문. 한 줄은 필수, 설명과 분야(특기 대분류 ID)는 선택이다. */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StoreConcernSaveRequest {

    @NotBlank
    @Size(max = 60)
    private String title;

    @Size(max = 500)
    private String description;

    @Positive
    private Long specialtyCategoryId;

    public static StoreConcernSaveRequest of(String title, String description, Long specialtyCategoryId) {
        return StoreConcernSaveRequest.builder()
                .title(title)
                .description(description)
                .specialtyCategoryId(specialtyCategoryId)
                .build();
    }

    public SaveStoreConcernCommand toCommand(String username) {
        return SaveStoreConcernCommand.of(username, title.trim(), normalizeOptional(description), specialtyCategoryId);
    }

    // 생략·null·빈 문자열·공백만 있는 설명은 모두 삭제(null)로 본다
    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
