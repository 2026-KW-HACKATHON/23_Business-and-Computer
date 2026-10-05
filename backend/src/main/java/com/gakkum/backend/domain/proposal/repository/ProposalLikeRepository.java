package com.gakkum.backend.domain.proposal.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.proposal.entity.ProposalLike;

public interface ProposalLikeRepository extends JpaRepository<ProposalLike, Long> {

    List<ProposalLike> findByStudentProfileIdAndProposalIdIn(Long studentProfileId, Collection<Long> proposalIds);

    /** 학생이 한 제안에 남긴 공감 기록. 유니크 제약으로 최대 한 건이다. */
    Optional<ProposalLike> findByProposalIdAndStudentProfileId(Long proposalId, Long studentProfileId);
}
