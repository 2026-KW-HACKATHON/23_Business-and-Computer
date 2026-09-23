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

import com.gakkum.backend.domain.student.dto.StudentCommandDto.CreateStudentProfileCommand;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentRegistrationRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Email
    @Size(max = 255)
    @Pattern(regexp = "(?i)^[^@\\s]+@kw\\.ac\\.kr$")
    private String email;

    @NotBlank
    @Pattern(regexp = "^광운대학교$")
    private String university;

    @NotBlank
    @Pattern(regexp = "^\\d{10}$")
    private String studentNumber;

    @NotBlank
    @Size(max = 255)
    private String major;

    @Size(max = 255)
    @Pattern(regexp = "^$|^https?://\\S+$")
    private String portfolioUrl;

    private String introduction;

    @Size(max = 255)
    @Pattern(regexp = "^$|^https?://\\S+$")
    private String profileImageUrl;

    private List<@NotNull @Positive Long> specialtyIds;

    private List<@Valid CertificateRequest> certificates;

    public static StudentRegistrationRequest of(
            String name,
            String email,
            String university,
            String studentNumber,
            String major,
            String portfolioUrl,
            String introduction,
            String profileImageUrl,
            List<Long> specialtyIds,
            List<CertificateRequest> certificates) {
        return StudentRegistrationRequest.builder()
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

    public String getStudentName() {
        return name.trim();
    }

    public String getNormalizedEmail() {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public List<Long> getNormalizedSpecialtyIds() {
        return specialtyIds == null ? List.of() : List.copyOf(specialtyIds);
    }

    public List<CertificateRequest> getNormalizedCertificates() {
        return certificates == null ? List.of() : certificates;
    }

    public CreateStudentProfileCommand toCommand(String userId) {
        return CreateStudentProfileCommand.of(
                userId,
                university,
                studentNumber,
                major.trim(),
                normalizeOptional(portfolioUrl),
                normalizeOptional(introduction),
                normalizeOptional(profileImageUrl));
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CertificateRequest {

        @NotBlank
        @Size(max = 255)
        private String certificateName;

        @NotNull
        @Min(1900)
        private Integer acquiredYear;

        @NotBlank
        @Size(max = 255)
        private String issuingOrganization;

        public static CertificateRequest of(String certificateName, Integer acquiredYear, String issuingOrganization) {
            return CertificateRequest.builder()
                    .certificateName(certificateName)
                    .acquiredYear(acquiredYear)
                    .issuingOrganization(issuingOrganization)
                    .build();
        }

        public String getNormalizedCertificateName() {
            return certificateName.trim();
        }

        public String getNormalizedIssuingOrganization() {
            return issuingOrganization.trim();
        }
    }
}
