package com.gakkum.backend.application.proposal.dto;

import java.util.List;

import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ReceivedProposalListResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ReceivedProposalResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ReceivedProposalStudentResult;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReceivedProposalListResponse {

    private final List<ReceivedProposal> proposals;

    public static ReceivedProposalListResponse from(ReceivedProposalListResult result) {
        return new ReceivedProposalListResponse(
                result.getProposals().stream().map(ReceivedProposal::from).toList());
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReceivedProposal {

        private final Long proposalId;
        private final String title;
        private final ProposalStatus status;
        private final Integer likeCount;
        private final List<ProposalDetailResponse.SpecialtyCategory> specialtyCategories;
        private final String proposedSolution;
        private final ReceivedProposalStudent student;
        // 결제로 만들어진 의뢰. 결제 전이면 null
        private final Long jobId;

        public static ReceivedProposal from(ReceivedProposalResult result) {
            return new ReceivedProposal(
                    result.getProposalId(),
                    result.getTitle(),
                    result.getStatus(),
                    result.getLikeCount(),
                    result.getSpecialtyCategories().stream()
                            .map(ProposalDetailResponse.SpecialtyCategory::from)
                            .toList(),
                    result.getProposedSolution(),
                    ReceivedProposalStudent.from(result.getStudent()),
                    result.getJobId());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReceivedProposalStudent {

        private final Long studentProfileId;
        private final String name;
        private final String studentNumber;
        private final String major;

        public static ReceivedProposalStudent from(ReceivedProposalStudentResult result) {
            return new ReceivedProposalStudent(
                    result.getStudentProfileId(), result.getName(), result.getStudentNumber(), result.getMajor());
        }
    }
}
