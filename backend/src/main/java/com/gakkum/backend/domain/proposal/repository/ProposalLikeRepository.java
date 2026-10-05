package com.gakkum.backend.domain.proposal.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.proposal.entity.ProposalLike;

public interface ProposalLikeRepository extends JpaRepository<ProposalLike, Long> {

    List<ProposalLike> findByStudentProfileIdAndProposalIdIn(Long studentProfileId, Collection<Long> proposalIds);
}
