package com.gakkum.backend.domain.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class StudentCommandDto {

    private StudentCommandDto() {
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CreateStudentProfileCommand {

        private final String userId;
        private final String university;
        private final String studentNumber;
        private final String major;
        private final String portfolioUrl;
        private final String introduction;
        private final String profileImageUrl;
    }
}
