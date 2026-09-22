package com.gakkum.backend.domain.specialty.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class SpecialtyCommandDto {

    private SpecialtyCommandDto() {
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class AddStudentSpecialtyCommand {

        private final Long studentProfileId;
        private final Long specialtyId;
    }
}
