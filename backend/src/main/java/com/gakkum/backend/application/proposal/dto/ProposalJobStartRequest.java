package com.gakkum.backend.application.proposal.dto;

import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.StartProposalJobCommand;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProposalJobStartRequest {

    @NotNull
    @AssertTrue
    private Boolean deadlineAndPenaltyAgreed;

    public static ProposalJobStartRequest of(Boolean deadlineAndPenaltyAgreed) {
        return ProposalJobStartRequest.builder()
                .deadlineAndPenaltyAgreed(deadlineAndPenaltyAgreed)
                .build();
    }

    public StartProposalJobCommand toCommand(String username, Long jobId) {
        return StartProposalJobCommand.of(username, jobId);
    }
}
