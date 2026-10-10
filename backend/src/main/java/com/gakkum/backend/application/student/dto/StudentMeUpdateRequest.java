package com.gakkum.backend.application.student.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.gakkum.backend.domain.student.dto.StudentCommandDto.UpdateStudentCertificateCommand;
import com.gakkum.backend.domain.student.dto.StudentCommandDto.UpdateStudentMeCommand;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentMeUpdateRequest {

    @Size(max = 255)
    @Pattern(regexp = "^$|^https?://\\S+$")
    private String profileImageUrl;

    @Size(max = 5000)
    private String introduction;

    // 전체 저장이라 목록은 생략할 수 없다. 빈 목록은 기존 목록 전체 삭제다
    @NotNull
    private List<@NotNull @Positive Long> specialtyIds;

    @NotNull
    private List<@NotNull @Valid CertificateRequest> certificates;

    @Size(max = 255)
    @Pattern(regexp = "^$|^https?://\\S+$")
    private String portfolioUrl;

    public static StudentMeUpdateRequest of(
            String profileImageUrl,
            String introduction,
            List<Long> specialtyIds,
            List<CertificateRequest> certificates,
            String portfolioUrl) {
        return StudentMeUpdateRequest.builder()
                .profileImageUrl(profileImageUrl)
                .introduction(introduction)
                .specialtyIds(specialtyIds)
                .certificates(certificates)
                .portfolioUrl(portfolioUrl)
                .build();
    }

    public UpdateStudentMeCommand toCommand(String username) {
        return UpdateStudentMeCommand.of(
                username,
                normalizeOptional(profileImageUrl),
                normalizeOptional(introduction),
                normalizeOptional(portfolioUrl),
                specialtyIds,
                certificates.stream()
                        .map(certificate -> UpdateStudentCertificateCommand.of(
                                certificate.getCertificateName().trim(),
                                certificate.getAcquiredYear()))
                        .toList());
    }

    // 생략·null·빈 문자열·공백만 있는 값은 모두 삭제(null)로 본다
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

        public static CertificateRequest of(String certificateName, Integer acquiredYear) {
            return CertificateRequest.builder()
                    .certificateName(certificateName)
                    .acquiredYear(acquiredYear)
                    .build();
        }
    }
}
