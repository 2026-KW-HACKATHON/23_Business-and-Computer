package com.gakkum.backend.domain.specialty.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class SpecialtyQueryDto {

    private SpecialtyQueryDto() {
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyListResponse {

        private final List<SpecialtyCategoryResponse> categories;

        public static SpecialtyListResponse from(List<SpecialtyCategoryResponse> categories) {
            return new SpecialtyListResponse(categories);
        }

        // data 필드가 객체가 아닌 배열([{...}, {...}])로 직렬화되도록 지정
        @JsonValue
        public List<SpecialtyCategoryResponse> getCategories() {
            return categories;
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyCategoryResponse {

        private final Long id;
        private final String name;
        private final List<SpecialtyResponse> specialties;

        public static SpecialtyCategoryResponse of(Long id, String name, List<SpecialtyResponse> specialties) {
            return SpecialtyCategoryResponse.builder()
                    .id(id)
                    .name(name)
                    .specialties(specialties)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyResponse {

        private final Long id;
        private final String name;

        public static SpecialtyResponse of(Long id, String name) {
            return SpecialtyResponse.builder()
                    .id(id)
                    .name(name)
                    .build();
        }
    }
}
