package com.gakkum.backend.domain.proposal.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.proposal.entity.Proposal;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    Optional<Proposal> findByIdAndOwnerProfileId(Long id, Long ownerProfileId);
}
