package com.gakkum.backend.application.owner.dto;

import java.time.OffsetDateTime;

import com.gakkum.backend.domain.owner.dto.OwnerQueryDto.StoreConcernResult;
import com.gakkum.backend.global.response.KoreaTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 가게 고민. 설명이 없으면 description이, 분야를 고르지 않았으면 specialtyCategory가 null이다. */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StoreConcernResponse {

    private final Long concernId;
    private final String title;
    private final String description;
    private final SpecialtyCategory specialtyCategory;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public static StoreConcernResponse from(StoreConcernResult result) {
        return StoreConcernResponse.builder()
                .concernId(result.getConcernId())
                .title(result.getTitle())
                .description(result.getDescription())
                .specialtyCategory(result.getSpecialtyCategoryId() == null
                        ? null
                        : new SpecialtyCategory(result.getSpecialtyCategoryId(), result.getSpecialtyCategoryName()))
                .createdAt(KoreaTime.from(result.getCreatedAt()))
                .updatedAt(KoreaTime.from(result.getUpdatedAt()))
                .build();
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyCategory {

        private final Long id;
        private final String name;
    }
}
