package com.gakkum.backend.domain.proposal.dto;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class ProposalCommandDto {

    private ProposalCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CreateProposalCommand {

        private final String username;
        private final Long ownerProfileId;
        private final List<Long> specialtyIds;
        private final String title;
        private final String customerProblem;
        private final String proposedSolution;
        private final String workPlan;
        private final Long proposedFee;
        private final Integer draftDays;
        private final Integer finalDays;
        private final List<String> referenceImageUrls;

        public static CreateProposalCommand of(
                String username,
                Long ownerProfileId,
                List<Long> specialtyIds,
                String title,
                String customerProblem,
                String proposedSolution,
                String workPlan,
                Long proposedFee,
                Integer draftDays,
                Integer finalDays,
                List<String> referenceImageUrls) {
            return CreateProposalCommand.builder()
                    .username(username)
                    .ownerProfileId(ownerProfileId)
                    .specialtyIds(List.copyOf(specialtyIds))
                    .title(title)
                    .customerProblem(customerProblem)
                    .proposedSolution(proposedSolution)
                    .workPlan(workPlan)
                    .proposedFee(proposedFee)
                    .draftDays(draftDays)
                    .finalDays(finalDays)
                    .referenceImageUrls(List.copyOf(referenceImageUrls))
                    .build();
        }
    }
}
