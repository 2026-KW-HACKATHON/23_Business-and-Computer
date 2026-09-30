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

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "proposal_specialties", uniqueConstraints = @UniqueConstraint(
        name = "proposal_specialties_proposal_id_specialty_id_key",
        columnNames = { "proposal_id", "specialty_id" }))
public class ProposalSpecialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_id", nullable = false)
    private Long proposalId;

    @Column(name = "specialty_id", nullable = false)
    private Long specialtyId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static ProposalSpecialty create(Long proposalId, Long specialtyId) {
        return ProposalSpecialty.builder()
                .proposalId(proposalId)
                .specialtyId(specialtyId)
                .build();
    }
}
