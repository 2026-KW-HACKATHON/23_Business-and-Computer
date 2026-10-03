package com.gakkum.backend.application.proposal.dto;

import java.util.List;

import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.MyProposalListResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.MyProposalResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalStoreResult;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MyProposalListResponse {

    private final List<MyProposal> proposals;

    public static MyProposalListResponse from(MyProposalListResult result) {
        return new MyProposalListResponse(result.getProposals().stream().map(MyProposal::from).toList());
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MyProposal {

        private final Long proposalId;
        private final String title;
        private final ProposalStatus status;
        private final Integer likeCount;
        private final List<ProposalDetailResponse.SpecialtyCategory> specialtyCategories;
        private final String proposedSolution;
        private final Store store;

        public static MyProposal from(MyProposalResult result) {
            return new MyProposal(
                    result.getProposalId(),
                    result.getTitle(),
                    result.getStatus(),
                    result.getLikeCount(),
                    result.getSpecialtyCategories().stream()
                            .map(ProposalDetailResponse.SpecialtyCategory::from)
                            .toList(),
                    result.getProposedSolution(),
                    Store.from(result.getStore()));
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Store {

        private final Long ownerProfileId;
        private final String storeName;
        private final String storeAddress;
        private final String profileImageUrl;

        public static Store from(ProposalStoreResult result) {
            return new Store(result.getOwnerProfileId(), result.getStoreName(),
                    result.getStoreAddress(), result.getProfileImageUrl());
        }
    }
}
