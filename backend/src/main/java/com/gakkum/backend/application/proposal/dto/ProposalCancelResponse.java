package com.gakkum.backend.application.proposal.dto;

import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCancelResult;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProposalCancelResponse {

    private final Long proposalId;
    private final ProposalStatus status;

    public static ProposalCancelResponse from(ProposalCancelResult result) {
        return ProposalCancelResponse.builder()
                .proposalId(result.getProposalId())
                .status(result.getStatus())
                .build();
    }
}
