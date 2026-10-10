package com.gakkum.backend.application.explore.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.gakkum.backend.application.explore.dto.ExploreQueryDto.BusinessCategoryResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.StoreExploreResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.StoreItemResult;
import com.gakkum.backend.application.owner.dto.StoreConcernResponse;
import com.gakkum.backend.global.response.KoreaTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 매장 목록 한 페이지. 사진이나 주소가 없으면 해당 필드는 null이다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StoreExploreResponse {

    private final List<Item> items;
    private final String nextCursor;
    private final boolean hasNext;

    public static StoreExploreResponse from(StoreExploreResult result) {
        return new StoreExploreResponse(
                result.getItems().stream().map(Item::from).toList(),
                result.getNextCursor(),
                result.isHasNext());
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Item {

        private final String storeName;
        private final String profileImageUrl;
        private final BusinessCategory businessCategory;
        private final String storeAddress;
        private final Long ownerProfileId;
        private final OffsetDateTime createdAt;
        // 해결되지 않은 가게 고민. 없으면 null
        private final StoreConcernResponse concern;

        public static Item from(StoreItemResult result) {
            return Item.builder()
                    .storeName(result.getStoreName())
                    .profileImageUrl(result.getProfileImageUrl())
                    .businessCategory(BusinessCategory.from(result.getBusinessCategory()))
                    .storeAddress(result.getStoreAddress())
                    .ownerProfileId(result.getOwnerProfileId())
                    .createdAt(KoreaTime.from(result.getCreatedAt()))
                    .concern(result.getConcern() == null ? null : StoreConcernResponse.from(result.getConcern()))
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class BusinessCategory {

        private final Long id;
        private final String name;

        public static BusinessCategory from(BusinessCategoryResult result) {
            return new BusinessCategory(result.getId(), result.getName());
        }
    }
}
