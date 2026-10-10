package com.gakkum.backend.domain.owner.dto;

import java.time.LocalDateTime;

import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.entity.StoreConcern;
import com.gakkum.backend.domain.user.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class OwnerQueryDto {

    private OwnerQueryDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OwnerMeResult {

        private final Long ownerProfileId;
        private final String profileImageUrl;
        // 사업자등록상 대표자명이 아닌 가입자 이름. 대표자명은 representativeName
        private final String name;
        // 저장된 대표자 이름이 없으면 가입자 이름으로 대체된 값
        private final String representativeName;
        private final String businessNumber;
        private final String storeName;
        private final String storeAddress;
        private final Long categoryId;
        private final String description;
        private final Long sentJobCount;
        private final Long receivedProposalCount;
        private final Long inProgressJobCount;
        private final Long completedJobCount;

        /** 보낸 의뢰 수는 취소만 뺀 누적 수라 진행 중·완료 수를 포함한다. */
        public static OwnerMeResult of(
                Owner owner,
                User user,
                String representativeName,
                long sentJobCount,
                long receivedProposalCount,
                long inProgressJobCount,
                long completedJobCount) {
            return OwnerMeResult.builder()
                    .ownerProfileId(owner.getId())
                    .profileImageUrl(owner.getProfileImageUrl())
                    .name(user.getName())
                    .representativeName(representativeName)
                    .businessNumber(owner.getBusinessNumber())
                    .storeName(owner.getStoreName())
                    .storeAddress(owner.getStoreAddress())
                    .categoryId(owner.getCategoryId())
                    .description(owner.getDescription())
                    .sentJobCount(sentJobCount)
                    .receivedProposalCount(receivedProposalCount)
                    .inProgressJobCount(inProgressJobCount)
                    .completedJobCount(completedJobCount)
                    .build();
        }
    }

    /** 가게 고민. 분야를 고르지 않았으면 분야 ID·이름이 null이다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StoreConcernResult {

        private final Long concernId;
        private final Long ownerProfileId;
        private final String title;
        private final String description;
        private final Long specialtyCategoryId;
        private final String specialtyCategoryName;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public static StoreConcernResult of(StoreConcern concern, String specialtyCategoryName) {
            return StoreConcernResult.builder()
                    .concernId(concern.getId())
                    .ownerProfileId(concern.getOwnerProfileId())
                    .title(concern.getTitle())
                    .description(concern.getDescription())
                    .specialtyCategoryId(concern.getSpecialtyCategoryId())
                    .specialtyCategoryName(specialtyCategoryName)
                    .createdAt(concern.getCreatedAt())
                    .updatedAt(concern.getUpdatedAt())
                    .build();
        }
    }
}
