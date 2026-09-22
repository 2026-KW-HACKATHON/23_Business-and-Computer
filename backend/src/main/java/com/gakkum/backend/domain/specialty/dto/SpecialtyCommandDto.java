package com.gakkum.backend.domain.specialty.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class SpecialtyCommandDto {

    private SpecialtyCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class AddStudentSpecialtyCommand {

        private final Long studentProfileId;
        private final Long specialtyId;

        public static AddStudentSpecialtyCommand of(Long studentProfileId, Long specialtyId) {
            return AddStudentSpecialtyCommand.builder()
                    .studentProfileId(studentProfileId)
                    .specialtyId(specialtyId)
                    .build();
        }
    }
}
