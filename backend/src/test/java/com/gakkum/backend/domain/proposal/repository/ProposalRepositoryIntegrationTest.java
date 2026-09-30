package com.gakkum.backend.domain.proposal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalLike;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.proposal.service.ProposalService;

/** 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백된다. */
@SpringBootTest
@Transactional
class ProposalRepositoryIntegrationTest {

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private ProposalSpecialtyRepository proposalSpecialtyRepository;

    @Autowired
    private ProposalLikeRepository proposalLikeRepository;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("PostgreSQL에 제안과 소분류를 함께 저장하고 사진 URL을 JSONB 배열로 같은 순서로 읽는다")
    void storesProposalWithSpecialtiesAndImages() {
        Proposal saved = proposalService.createProposal(command(List.of(1L, 2L),
                List.of("https://bucket/b.png", "https://bucket/a.png")), 7L);
        proposalRepository.flush();

        Proposal found = proposalRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStudentProfileId()).isEqualTo(7L);
        assertThat(found.getOwnerProfileId()).isEqualTo(5L);
        assertThat(found.getCustomerProblem()).hasSize(500);
        assertThat(found.getReferenceImageUrls()).containsExactly("https://bucket/b.png", "https://bucket/a.png");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getLikeCount()).isZero();
        assertThat(proposalSpecialtyRepository.findByProposalId(saved.getId()))
                .extracting(ProposalSpecialty::getSpecialtyId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("PostgreSQL은 사진이 없는 제안을 빈 JSONB 배열로 저장하고 같은 학생의 반복 제안을 허용한다")
    void storesEmptyImagesAndRepeatedProposals() {
        Proposal first = proposalService.createProposal(command(List.of(1L), List.of()), 7L);
        Proposal second = proposalService.createProposal(command(List.of(1L), List.of()), 7L);
        proposalRepository.flush();

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(proposalRepository.findById(first.getId()).orElseThrow().getReferenceImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("PostgreSQL은 한 제안에 같은 소분류를 두 번 저장하면 유니크 제약으로 거부한다")
    void rejectsDuplicateSpecialtyInProposal() {
        Proposal saved = proposalService.createProposal(command(List.of(1L), List.of()), 7L);
        proposalRepository.flush();

        assertThatThrownBy(() -> proposalSpecialtyRepository.saveAndFlush(ProposalSpecialty.create(saved.getId(), 1L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL은 최종 기간이 초안 기간보다 짧은 제안을 체크 제약으로 거부한다")
    void rejectsInvalidDayOrder() {
        assertThatThrownBy(() -> proposalRepository.saveAndFlush(Proposal.create(
                7L, 5L, "제목", "문제", "해결", "계획", 1L, 5, 4, List.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL은 0 이하의 작업비를 체크 제약으로 거부한다")
    void rejectsNonPositiveFee() {
        assertThatThrownBy(() -> proposalRepository.saveAndFlush(Proposal.create(
                7L, 5L, "제목", "문제", "해결", "계획", 0L, 0, 0, List.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL은 500자를 넘는 내용을 거부한다")
    void rejectsTooLongContent() {
        assertThatThrownBy(() -> proposalRepository.saveAndFlush(Proposal.create(
                7L, 5L, "제목", "가".repeat(501), "해결", "계획", 1L, 0, 0, List.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL은 한 제안에 여러 학생의 좋아요를 저장하고 같은 학생의 두 번째 좋아요는 유니크 제약으로 거부한다")
    void storesLikesOncePerStudent() {
        Proposal saved = proposalService.createProposal(command(List.of(1L), List.of()), 7L);
        proposalLikeRepository.saveAndFlush(ProposalLike.create(saved.getId(), 8L));
        ProposalLike second = proposalLikeRepository.saveAndFlush(ProposalLike.create(saved.getId(), 9L));

        assertThat(second.getCreatedAt()).isNotNull();
        assertThat(proposalLikeRepository.findAll())
                .filteredOn(like -> like.getProposalId().equals(saved.getId()))
                .extracting(ProposalLike::getStudentProfileId)
                .containsExactlyInAnyOrder(8L, 9L);
        assertThatThrownBy(() -> proposalLikeRepository.saveAndFlush(ProposalLike.create(saved.getId(), 8L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL은 음수 좋아요 수를 체크 제약으로 거부한다")
    void rejectsNegativeLikeCount() {
        Proposal saved = proposalService.createProposal(command(List.of(1L), List.of()), 7L);
        proposalRepository.flush();

        assertThatThrownBy(() -> entityManager
                .createNativeQuery("UPDATE proposals SET like_count = -1 WHERE id = :id")
                .setParameter("id", saved.getId())
                .executeUpdate())
                .hasStackTraceContaining("proposals_like_count_check");
    }

    private CreateProposalCommand command(List<Long> specialtyIds, List<String> imageUrls) {
        return CreateProposalCommand.of("KAKAO_12345", 5L, specialtyIds, "메뉴판 개선 제안", "가".repeat(500),
                "사진 메뉴판으로 바꿉니다.", "촬영 후 편집합니다.", 50000L, 0, 7, imageUrls);
    }
}
