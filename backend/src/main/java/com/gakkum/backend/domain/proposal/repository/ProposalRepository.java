package com.gakkum.backend.domain.proposal.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.proposal.entity.Proposal;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {
}
