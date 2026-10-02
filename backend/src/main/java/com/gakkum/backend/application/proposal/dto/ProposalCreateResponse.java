package com.gakkum.backend.application.proposal.dto;

import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCreateResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProposalCreateResponse {

    private final Long proposalId;

    public static ProposalCreateResponse from(ProposalCreateResult result) {
        return ProposalCreateResponse.builder()
                .proposalId(result.getProposalId())
                .build();
    }
}
