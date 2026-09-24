package com.gakkum.backend.application.auth.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StudentEmailVerifyRequest(
        @NotBlank @Email @Size(max = 255) @Pattern(regexp = "(?i)^[^@\\s]+@kw\\.ac\\.kr$") String email,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String code) {

    public String normalizedEmail() {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
