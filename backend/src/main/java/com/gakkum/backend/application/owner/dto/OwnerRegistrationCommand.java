package com.gakkum.backend.application.owner.dto;

import java.time.LocalDate;
import java.util.List;

public record OwnerRegistrationCommand(
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
}
