package com.gakkum.backend.application.proposal.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.MyProposalListResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.MyProposalResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalStoreResult;
import com.gakkum.backend.domain.proposal.entity.ProposalRejectedBy;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.global.response.KoreaTime;

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
        // 결제로 만들어진 의뢰. 결제 전이면 null
        private final Long jobId;
        private final JobStatus jobStatus;
        private final OffsetDateTime createdAt;
        // 거절한 주체와 거절 시각. 거절되지 않았거나 기록 전에 거절된 제안은 null
        private final ProposalRejectedBy rejectedBy;
        private final OffsetDateTime rejectedAt;

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
                    Store.from(result.getStore()),
                    result.getJobId(),
                    result.getJobStatus(),
                    KoreaTime.from(result.getCreatedAt()),
                    result.getRejectedBy(),
                    KoreaTime.from(result.getRejectedAt()));
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
