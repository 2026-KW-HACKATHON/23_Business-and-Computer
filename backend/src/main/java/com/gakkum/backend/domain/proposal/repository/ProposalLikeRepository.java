package com.gakkum.backend.domain.proposal.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gakkum.backend.domain.proposal.entity.ProposalLike;

public interface ProposalLikeRepository extends JpaRepository<ProposalLike, Long> {

    List<ProposalLike> findByStudentProfileIdAndProposalIdIn(Long studentProfileId, Collection<Long> proposalIds);

    /** 학생이 한 제안에 남긴 공감 기록. 유니크 제약으로 최대 한 건이다. */
    Optional<ProposalLike> findByProposalIdAndStudentProfileId(Long proposalId, Long studentProfileId);

    /** 제안에 남은 모든 학생의 공감 기록을 한 번에 지운다. 제안 취소에 쓴다. */
    @Modifying
    @Query("delete from ProposalLike pl where pl.proposalId = :proposalId")
    void deleteAllByProposalId(@Param("proposalId") Long proposalId);
}
