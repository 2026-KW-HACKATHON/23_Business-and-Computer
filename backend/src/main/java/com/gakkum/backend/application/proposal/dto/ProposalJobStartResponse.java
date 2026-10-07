package com.gakkum.backend.application.proposal.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalJobStartResult;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.global.response.KoreaTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProposalJobStartResponse {

    private final Long jobId;
    private final JobStatus jobStatus;
    private final ProposalStatus proposalStatus;
    private final OffsetDateTime startedAt;
    private final String chatRoomId;
    private final LocalDate draftDeadline;
    private final LocalDate finalDeadline;

    public static ProposalJobStartResponse from(ProposalJobStartResult result) {
        return ProposalJobStartResponse.builder()
                .jobId(result.getJobId())
                .jobStatus(result.getJobStatus())
                .proposalStatus(result.getProposalStatus())
                .startedAt(KoreaTime.from(result.getStartedAt()))
                .chatRoomId(result.getChatRoomId())
                .draftDeadline(result.getDraftDeadline())
                .finalDeadline(result.getFinalDeadline())
                .build();
    }
}
