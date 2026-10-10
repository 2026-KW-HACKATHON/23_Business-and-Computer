package com.gakkum.backend.application.owner.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.gakkum.backend.domain.auth.dto.AuthCommandDto.VerifyOwnerBusinessCommand;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;

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

    // 국세청 진위 확인에 개업일과 대표자 이름이 함께 필요해 필수로 받는다
    @NotNull
    @PastOrPresent
    private LocalDate openedAt;

    @NotBlank
    @Size(max = 255)
    private String representativeName;

    @Size(max = 5000)
    private String description;

    @Size(max = 5)
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

    public String getOwnerName() {
        return name.trim();
    }

    public String getNormalizedBusinessNumber() {
        return businessNumber.replace("-", "");
    }

    /**
     * 가입 전에 사업자등록정보 진위 확인에 넘길 값. 사업자등록증에 적힌 대표자 이름을 그대로 쓰고,
     * 가입하는 사람의 이름(name)과는 비교하지 않는다.
     */
    public VerifyOwnerBusinessCommand toBusinessVerificationCommand(String username) {
        return VerifyOwnerBusinessCommand.of(
                username,
                representativeName.trim(),
                openedAt,
                getNormalizedBusinessNumber());
    }

    public CreateOwnerProfileCommand toCommand(String userId) {
        List<String> normalizedStoreImageUrls = storeImageUrls == null
                ? List.of()
                : storeImageUrls.stream().map(String::trim).toList();

        return CreateOwnerProfileCommand.of(
                userId,
                getNormalizedBusinessNumber(),
                openedAt,
                normalizeOptional(representativeName),
                storeName.trim(),
                categoryId,
                normalizeOptional(storeAddress),
                normalizeOptional(description),
                normalizeOptional(profileImageUrl),
                normalizedStoreImageUrls);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
