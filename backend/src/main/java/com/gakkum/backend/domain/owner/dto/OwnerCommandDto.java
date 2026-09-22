package com.gakkum.backend.domain.owner.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class OwnerCommandDto {

    private OwnerCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CreateOwnerProfileCommand {

        private final String userId;
        private final String businessNumber;
        private final LocalDate openedAt;
        private final String representativeName;
        private final String storeName;
        private final Long categoryId;
        private final String storeAddress;
        private final String description;
        private final String profileImageUrl;
        private final List<String> storeImageUrls;

        public static CreateOwnerProfileCommand of(String userId, String businessNumber, LocalDate openedAt, String representativeName, String storeName, Long categoryId, String storeAddress, String description, String profileImageUrl, List<String> storeImageUrls) {
            return CreateOwnerProfileCommand.builder()
                    .userId(userId)
                    .businessNumber(businessNumber)
                    .openedAt(openedAt)
                    .representativeName(representativeName)
                    .storeName(storeName)
                    .categoryId(categoryId)
                    .storeAddress(storeAddress)
                    .description(description)
                    .profileImageUrl(profileImageUrl)
                    .storeImageUrls(storeImageUrls)
                    .build();
        }
    }
}
