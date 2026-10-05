package com.gakkum.backend.application.proposal.dto;

import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalLikeResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProposalLikeResponse {

    private final Long proposalId;
    private final Integer likeCount;
    private final boolean likedByMe;

    public static ProposalLikeResponse from(ProposalLikeResult result) {
        return ProposalLikeResponse.builder()
                .proposalId(result.getProposalId())
                .likeCount(result.getLikeCount())
                .likedByMe(result.isLikedByMe())
                .build();
    }
}
