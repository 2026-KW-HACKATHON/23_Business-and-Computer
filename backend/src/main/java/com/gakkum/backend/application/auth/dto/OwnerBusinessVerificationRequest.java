package com.gakkum.backend.application.auth.dto;

import java.time.LocalDate;

import com.gakkum.backend.domain.auth.dto.AuthCommandDto.VerifyOwnerBusinessCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OwnerBusinessVerificationRequest {

    @NotBlank
    @Size(max = 255)
    private String representativeName;

    @NotNull
    @PastOrPresent
    private LocalDate openedAt;

    @NotBlank
    @Pattern(regexp = "^\\d{3}-?\\d{2}-?\\d{5}$")
    private String businessNumber;

    public static OwnerBusinessVerificationRequest of(
            String representativeName, LocalDate openedAt, String businessNumber) {
        return OwnerBusinessVerificationRequest.builder()
                .representativeName(representativeName)
                .openedAt(openedAt)
                .businessNumber(businessNumber)
                .build();
    }

    public VerifyOwnerBusinessCommand toCommand(String username) {
        return VerifyOwnerBusinessCommand.of(
                username,
                representativeName.trim(),
                openedAt,
                businessNumber.replace("-", ""));
    }
}
