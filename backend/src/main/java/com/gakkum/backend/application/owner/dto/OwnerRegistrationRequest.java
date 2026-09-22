package com.gakkum.backend.application.owner.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OwnerRegistrationRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String storeName,
        @Size(max = 255) String storeAddress,
        @NotNull @Positive Long categoryId,
        @NotBlank @Pattern(regexp = "^\\d{3}-?\\d{2}-?\\d{5}$") String businessNumber,
        @PastOrPresent LocalDate openedAt,
        @Size(max = 255) String representativeName,
        String description,
        List<@NotBlank @Size(max = 255) @Pattern(regexp = "^https?://\\S+$") String> storeImageUrls,
        @Size(max = 255) @Pattern(regexp = "^$|^https?://\\S+$") String profileImageUrl) {

    public OwnerRegistrationCommand toCommand() {
        List<String> normalizedStoreImageUrls = storeImageUrls == null
                ? List.of()
                : storeImageUrls.stream().map(String::trim).toList();

        return new OwnerRegistrationCommand(
                name.trim(),
                storeName.trim(),
                normalizeOptional(storeAddress),
                categoryId,
                businessNumber.replace("-", ""),
                openedAt,
                normalizeOptional(representativeName),
                normalizeOptional(description),
                normalizedStoreImageUrls,
                normalizeOptional(profileImageUrl));
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
