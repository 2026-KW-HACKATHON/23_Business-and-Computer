package com.gakkum.backend.application.proposal.dto;

import java.time.OffsetDateTime;

import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalJobDeclineResult;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.global.response.KoreaTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProposalJobDeclineResponse {

    private final Long jobId;
    private final JobStatus jobStatus;
    private final ProposalStatus proposalStatus;
    private final Long paidAmount;
    private final Long studentCompensationAmount;
    private final Long refundAmount;
    private final OffsetDateTime declinedAt;

    public static ProposalJobDeclineResponse from(ProposalJobDeclineResult result) {
        return ProposalJobDeclineResponse.builder()
                .jobId(result.getJobId())
                .jobStatus(result.getJobStatus())
                .proposalStatus(result.getProposalStatus())
                .paidAmount(result.getPaidAmount())
                .studentCompensationAmount(result.getStudentCompensationAmount())
                .refundAmount(result.getRefundAmount())
                .declinedAt(KoreaTime.from(result.getDeclinedAt()))
                .build();
    }
}
