package com.gakkum.backend.domain.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;

class ProposalServiceTest {

    private final ProposalRepository proposalRepository = mock(ProposalRepository.class);
    private final ProposalSpecialtyRepository proposalSpecialtyRepository = mock(ProposalSpecialtyRepository.class);
    private final ProposalService proposalService = new ProposalService(proposalRepository, proposalSpecialtyRepository);

    @Test
    @DisplayName("발신 학생과 요청 값으로 좋아요 0개인 제안을 저장하고 저장된 제안 ID로 소분류를 저장한다")
    @SuppressWarnings("unchecked")
    void savesProposalAndSpecialties() {
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> {
            Proposal proposal = invocation.getArgument(0);
            return Proposal.builder()
                    .id(31L)
                    .studentProfileId(proposal.getStudentProfileId())
                    .ownerProfileId(proposal.getOwnerProfileId())
                    .build();
        });
        CreateProposalCommand command = CreateProposalCommand.of("KAKAO_12345", 5L, List.of(1L, 2L), "제목",
                "문제", "해결", "계획", 50000L, 0, 7, List.of("https://bucket/a.png"));

        Proposal saved = proposalService.createProposal(command, 7L);

        assertThat(saved.getId()).isEqualTo(31L);
        ArgumentCaptor<Proposal> proposalCaptor = ArgumentCaptor.forClass(Proposal.class);
        verify(proposalRepository).save(proposalCaptor.capture());
        Proposal proposal = proposalCaptor.getValue();
        assertThat(proposal.getStudentProfileId()).isEqualTo(7L);
        assertThat(proposal.getOwnerProfileId()).isEqualTo(5L);
        assertThat(proposal.getTitle()).isEqualTo("제목");
        assertThat(proposal.getCustomerProblem()).isEqualTo("문제");
        assertThat(proposal.getProposedSolution()).isEqualTo("해결");
        assertThat(proposal.getWorkPlan()).isEqualTo("계획");
        assertThat(proposal.getProposedFee()).isEqualTo(50000L);
        assertThat(proposal.getDraftDays()).isZero();
        assertThat(proposal.getFinalDays()).isEqualTo(7);
        assertThat(proposal.getReferenceImageUrls()).containsExactly("https://bucket/a.png");
        assertThat(proposal.getLikeCount()).isZero();

        ArgumentCaptor<List<ProposalSpecialty>> specialtiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(proposalSpecialtyRepository).saveAll(specialtiesCaptor.capture());
        assertThat(specialtiesCaptor.getValue())
                .extracting(ProposalSpecialty::getProposalId, ProposalSpecialty::getSpecialtyId)
                .containsExactly(
                        tuple(31L, 1L),
                        tuple(31L, 2L));
    }
}
