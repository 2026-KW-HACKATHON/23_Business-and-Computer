package com.gakkum.backend.domain.proposal.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학생이 제안에 누른 좋아요. 한 학생은 한 제안에 한 번만 누를 수 있다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "proposal_likes", uniqueConstraints = @UniqueConstraint(
        name = "proposal_likes_proposal_id_student_profile_id_key",
        columnNames = { "proposal_id", "student_profile_id" }))
public class ProposalLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_id", nullable = false)
    private Long proposalId;

    @Column(name = "student_profile_id", nullable = false)
    private Long studentProfileId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static ProposalLike create(Long proposalId, Long studentProfileId) {
        return ProposalLike.builder()
                .proposalId(proposalId)
                .studentProfileId(studentProfileId)
                .build();
    }
}
