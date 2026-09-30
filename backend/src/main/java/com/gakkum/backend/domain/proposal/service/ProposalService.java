package com.gakkum.backend.domain.proposal.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final ProposalSpecialtyRepository proposalSpecialtyRepository;

    /** 참조 ID와 사진 검증을 마친 제안과 소분류를 한 트랜잭션으로 저장한다. */
    @Transactional
    public Proposal createProposal(CreateProposalCommand command, Long studentProfileId) {
        Proposal proposal = proposalRepository.save(Proposal.create(
                studentProfileId,
                command.getOwnerProfileId(),
                command.getTitle(),
                command.getCustomerProblem(),
                command.getProposedSolution(),
                command.getWorkPlan(),
                command.getProposedFee(),
                command.getDraftDays(),
                command.getFinalDays(),
                command.getReferenceImageUrls()));

        List<ProposalSpecialty> specialties = command.getSpecialtyIds().stream()
                .map(specialtyId -> ProposalSpecialty.create(proposal.getId(), specialtyId))
                .toList();
        proposalSpecialtyRepository.saveAll(specialties);

        return proposal;
    }
}
