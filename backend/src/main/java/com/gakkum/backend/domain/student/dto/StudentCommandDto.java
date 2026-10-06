package com.gakkum.backend.domain.student.dto;

import java.util.List;

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

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class UpdateStudentMeCommand {

        private final String username;
        private final String profileImageUrl;
        private final String introduction;
        private final String portfolioUrl;
        private final List<Long> specialtyIds;
        private final List<UpdateStudentCertificateCommand> certificates;

        /** 문자열 세 항목의 null은 삭제, 빈 목록은 기존 목록 전체 삭제를 뜻한다. */
        public static UpdateStudentMeCommand of(
                String username,
                String profileImageUrl,
                String introduction,
                String portfolioUrl,
                List<Long> specialtyIds,
                List<UpdateStudentCertificateCommand> certificates) {
            return UpdateStudentMeCommand.builder()
                    .username(username)
                    .profileImageUrl(profileImageUrl)
                    .introduction(introduction)
                    .portfolioUrl(portfolioUrl)
                    .specialtyIds(List.copyOf(specialtyIds))
                    .certificates(List.copyOf(certificates))
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class UpdateStudentCertificateCommand {

        private final String certificateName;
        private final Integer acquiredYear;

        public static UpdateStudentCertificateCommand of(String certificateName, Integer acquiredYear) {
            return new UpdateStudentCertificateCommand(certificateName, acquiredYear);
        }
    }
}
