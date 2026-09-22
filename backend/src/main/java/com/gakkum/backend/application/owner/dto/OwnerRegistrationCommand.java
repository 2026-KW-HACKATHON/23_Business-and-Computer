package com.gakkum.backend.application.owner.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OwnerRegistrationCommand {

    private final String name;
    private final String storeName;
    private final String storeAddress;
    private final Long categoryId;
    private final String businessNumber;
    private final LocalDate openedAt;
    private final String representativeName;
    private final String description;
    private final List<String> storeImageUrls;
    private final String profileImageUrl;

    public static OwnerRegistrationCommand of(
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
        return OwnerRegistrationCommand.builder()
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
}
