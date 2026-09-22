package com.gakkum.backend.domain.certificate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class CertificateCommandDto {

    private CertificateCommandDto() {
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class AddStudentCertificateCommand {

        private final Long studentProfileId;
        private final String certificateName;
        private final Integer acquiredYear;
        private final String issuingOrganization;

        public static AddStudentCertificateCommand of(Long studentProfileId, String certificateName, Integer acquiredYear, String issuingOrganization) {
            return AddStudentCertificateCommand.builder()
                    .studentProfileId(studentProfileId)
                    .certificateName(certificateName)
                    .acquiredYear(acquiredYear)
                    .issuingOrganization(issuingOrganization)
                    .build();
        }
    }
}
