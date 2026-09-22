package com.gakkum.backend.application.student.dto;

import java.util.List;
import java.util.Locale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StudentRegistrationRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 255)
        @Pattern(regexp = "(?i)^[^@\\s]+@kw\\.ac\\.kr$") String email,
        @NotBlank @Pattern(regexp = "^광운대학교$") String university,
        @NotBlank @Pattern(regexp = "^\\d{10}$") String studentNumber,
        @NotBlank @Size(max = 255) String major,
        @Size(max = 255) @Pattern(regexp = "^$|^https?://\\S+$") String portfolioUrl,
        String introduction,
        @Size(max = 255) @Pattern(regexp = "^$|^https?://\\S+$") String profileImageUrl,
        List<@NotNull @Positive Long> specialtyIds,
        List<@Valid CertificateRequest> certificates) {

    public StudentRegistrationCommand toCommand() {
        List<Long> normalizedSpecialtyIds = specialtyIds == null ? List.of() : List.copyOf(specialtyIds);
        List<StudentRegistrationCommand.CertificateCommand> normalizedCertificates = certificates == null
                ? List.of()
                : certificates.stream().map(CertificateRequest::toCommand).toList();

        return new StudentRegistrationCommand(
                name.trim(),
                email.trim().toLowerCase(Locale.ROOT),
                university,
                studentNumber,
                major.trim(),
                normalizeOptional(portfolioUrl),
                normalizeOptional(introduction),
                normalizeOptional(profileImageUrl),
                normalizedSpecialtyIds,
                normalizedCertificates);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record CertificateRequest(
            @NotBlank @Size(max = 255) String certificateName,
            @NotNull @Min(1900) Integer acquiredYear,
            @NotBlank @Size(max = 255) String issuingOrganization) {

        private StudentRegistrationCommand.CertificateCommand toCommand() {
            return new StudentRegistrationCommand.CertificateCommand(
                    certificateName.trim(),
                    acquiredYear,
                    issuingOrganization.trim());
        }
    }
}
