package com.gakkum.backend.application.student.dto;

import lombok.Getter;

import java.util.List;

public record StudentRegistrationCommand(
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

    public record CertificateCommand(
            String certificateName,
            Integer acquiredYear,
            String issuingOrganization) {
    }
}
