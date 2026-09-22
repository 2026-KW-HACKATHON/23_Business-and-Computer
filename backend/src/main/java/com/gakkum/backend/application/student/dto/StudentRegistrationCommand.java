package com.gakkum.backend.application.student.dto;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentRegistrationCommand {

    private final String name;
    private final String email;
    private final String university;
    private final String studentNumber;
    private final String major;
    private final String portfolioUrl;
    private final String introduction;
    private final String profileImageUrl;
    private final List<Long> specialtyIds;
    private final List<CertificateCommand> certificates;

    public static StudentRegistrationCommand of(
            String name,
            String email,
            String university,
            String studentNumber,
            String major,
            String portfolioUrl,
            String introduction,
            String profileImageUrl,
            List<Long> specialtyIds,
            List<CertificateCommand> certificates) {
        return StudentRegistrationCommand.builder()
                .name(name)
                .email(email)
                .university(university)
                .studentNumber(studentNumber)
                .major(major)
                .portfolioUrl(portfolioUrl)
                .introduction(introduction)
                .profileImageUrl(profileImageUrl)
                .specialtyIds(specialtyIds)
                .certificates(certificates)
                .build();
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CertificateCommand {

        private final String certificateName;
        private final Integer acquiredYear;
        private final String issuingOrganization;

        public static CertificateCommand of(String certificateName, Integer acquiredYear, String issuingOrganization) {
            return CertificateCommand.builder()
                    .certificateName(certificateName)
                    .acquiredYear(acquiredYear)
                    .issuingOrganization(issuingOrganization)
                    .build();
        }
    }
}
