package com.gakkum.backend.domain.proposal.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;

public interface ProposalSpecialtyRepository extends JpaRepository<ProposalSpecialty, Long> {
    List<ProposalSpecialty> findByProposalId(Long proposalId);

    List<ProposalSpecialty> findByProposalIdIn(Collection<Long> proposalIds);
}
