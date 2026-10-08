package com.gakkum.backend.application.proposal.dto;

import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalRejectResult;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProposalRejectResponse {

    private final Long proposalId;
    private final ProposalStatus status;

    public static ProposalRejectResponse from(ProposalRejectResult result) {
        return ProposalRejectResponse.builder()
                .proposalId(result.getProposalId())
                .status(result.getStatus())
                .build();
    }
}
