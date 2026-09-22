package com.gakkum.backend.application.owner.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
public class OwnerRegistrationRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String storeName;

    @Size(max = 255)
    private String storeAddress;

    @NotNull
    @Positive
    private Long categoryId;

    @NotBlank
    @Pattern(regexp = "^\\d{3}-?\\d{2}-?\\d{5}$")
    private String businessNumber;

    @PastOrPresent
    private LocalDate openedAt;

    @Size(max = 255)
    private String representativeName;

    private String description;

    private List<@NotBlank @Size(max = 255) @Pattern(regexp = "^https?://\\S+$") String> storeImageUrls;

    @Size(max = 255)
    @Pattern(regexp = "^$|^https?://\\S+$")
    private String profileImageUrl;

    public static OwnerRegistrationRequest of(
            String name,
            String storeName,
            String storeAddress,
            Long categoryId,
            String businessNumber,
            LocalDate openedAt,
            String representativeName,
            String description,
            List<String> storeImageUrls,
            String profileImageUrl) {
        return OwnerRegistrationRequest.builder()
                .name(name)
                .storeName(storeName)
                .storeAddress(storeAddress)
                .categoryId(categoryId)
                .businessNumber(businessNumber)
                .openedAt(openedAt)
                .representativeName(representativeName)
                .description(description)
                .storeImageUrls(storeImageUrls)
                .profileImageUrl(profileImageUrl)
                .build();
    }

    public OwnerRegistrationCommand toCommand() {
        List<String> normalizedStoreImageUrls = storeImageUrls == null
                ? List.of()
                : storeImageUrls.stream().map(String::trim).toList();

        return OwnerRegistrationCommand.of(
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
