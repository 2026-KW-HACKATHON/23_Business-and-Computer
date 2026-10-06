package com.gakkum.backend.application.owner.dto;

import com.gakkum.backend.domain.owner.dto.OwnerQueryDto.OwnerMeResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OwnerMeResponse {

    private final Long ownerProfileId;
    private final String profileImageUrl;
    private final String name;
    private final String storeName;
    private final String storeAddress;
    private final Long categoryId;
    private final String description;
    private final Long sentJobCount;
    private final Long receivedProposalCount;
    private final Long inProgressJobCount;
    private final Long completedJobCount;

    public static OwnerMeResponse from(OwnerMeResult result) {
        return OwnerMeResponse.builder()
                .ownerProfileId(result.getOwnerProfileId())
                .profileImageUrl(result.getProfileImageUrl())
                .name(result.getName())
                .storeName(result.getStoreName())
                .storeAddress(result.getStoreAddress())
                .categoryId(result.getCategoryId())
                .description(result.getDescription())
                .sentJobCount(result.getSentJobCount())
                .receivedProposalCount(result.getReceivedProposalCount())
                .inProgressJobCount(result.getInProgressJobCount())
                .completedJobCount(result.getCompletedJobCount())
                .build();
    }
}
