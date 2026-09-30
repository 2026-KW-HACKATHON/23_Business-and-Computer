package com.gakkum.backend.domain.proposal.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.proposal.entity.ProposalLike;

public interface ProposalLikeRepository extends JpaRepository<ProposalLike, Long> {
}
