package com.gakkum.backend.domain.student.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class StudentCommandDto {

    private StudentCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CreateStudentProfileCommand {

        private final String userId;
        private final String university;
        private final String studentNumber;
        private final String major;
        private final String portfolioUrl;
        private final String introduction;
        private final String profileImageUrl;

        public static CreateStudentProfileCommand of(String userId, String university, String studentNumber, String major, String portfolioUrl, String introduction, String profileImageUrl) {
            return CreateStudentProfileCommand.builder()
                    .userId(userId)
                    .university(university)
                    .studentNumber(studentNumber)
                    .major(major)
                    .portfolioUrl(portfolioUrl)
                    .introduction(introduction)
                    .profileImageUrl(profileImageUrl)
                    .build();
        }
    }
}
