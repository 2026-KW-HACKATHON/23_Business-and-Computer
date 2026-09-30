package com.gakkum.backend.domain.proposal.dto;

import com.gakkum.backend.domain.proposal.entity.Proposal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class ProposalQueryDto {

    private ProposalQueryDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalCreateResult {

        private final Long proposalId;

        public static ProposalCreateResult from(Proposal proposal) {
            return ProposalCreateResult.builder()
                    .proposalId(proposal.getId())
                    .build();
        }
    }
}
