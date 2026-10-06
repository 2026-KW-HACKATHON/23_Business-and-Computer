package com.gakkum.backend.domain.proposal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalLike;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.service.ProposalService;

/** 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백된다. */
@SpringBootTest
@ActiveProfiles("local")
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
                List.of("https://bucket/b.png", "https://bucket/a.png")), 7L, null);
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
        Proposal first = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        Proposal second = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        proposalRepository.flush();

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(proposalRepository.findById(first.getId()).orElseThrow().getReferenceImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("PostgreSQL은 한 제안에 같은 소분류를 두 번 저장하면 유니크 제약으로 거부한다")
    void rejectsDuplicateSpecialtyInProposal() {
        Proposal saved = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        proposalRepository.flush();

        assertThatThrownBy(() -> proposalSpecialtyRepository.saveAndFlush(ProposalSpecialty.create(saved.getId(), 1L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL은 최종 기간이 초안 기간보다 짧은 제안을 체크 제약으로 거부한다")
    void rejectsInvalidDayOrder() {
        assertThatThrownBy(() -> proposalRepository.saveAndFlush(Proposal.create(
                7L, 5L, "제목", "문제", "해결", "계획", 1L, 5, 4, List.of(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL은 0 이하의 작업비를 체크 제약으로 거부한다")
    void rejectsNonPositiveFee() {
        assertThatThrownBy(() -> proposalRepository.saveAndFlush(Proposal.create(
                7L, 5L, "제목", "문제", "해결", "계획", 0L, 0, 0, List.of(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL은 500자를 넘는 내용을 거부한다")
    void rejectsTooLongContent() {
        assertThatThrownBy(() -> proposalRepository.saveAndFlush(Proposal.create(
                7L, 5L, "제목", "가".repeat(501), "해결", "계획", 1L, 0, 0, List.of(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL은 한 제안에 여러 학생의 좋아요를 저장하고 같은 학생의 두 번째 좋아요는 유니크 제약으로 거부한다")
    void storesLikesOncePerStudent() {
        Proposal saved = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
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
    @DisplayName("PostgreSQL은 학생과 제안 ID 목록으로 조회하면 그 학생이 그 제안들에 남긴 공감 기록만 반환한다")
    void findsLikesByStudentAndProposalIds() {
        Proposal likedByMe = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        Proposal likedByBoth = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        Proposal likedByOther = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        Proposal notLiked = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        Proposal outOfPage = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        proposalLikeRepository.save(ProposalLike.create(likedByMe.getId(), 8L));
        proposalLikeRepository.save(ProposalLike.create(likedByBoth.getId(), 8L));
        proposalLikeRepository.save(ProposalLike.create(likedByBoth.getId(), 9L));
        proposalLikeRepository.save(ProposalLike.create(likedByOther.getId(), 9L));
        // 조회 목록에 없는 제안은 본인이 공감했어도 빠진다
        proposalLikeRepository.save(ProposalLike.create(outOfPage.getId(), 8L));
        proposalLikeRepository.flush();
        entityManager.clear();
        List<Long> proposalIds = List.of(
                likedByMe.getId(), likedByBoth.getId(), likedByOther.getId(), notLiked.getId());

        assertThat(proposalLikeRepository.findByStudentProfileIdAndProposalIdIn(8L, proposalIds))
                .allMatch(like -> like.getStudentProfileId().equals(8L))
                .extracting(ProposalLike::getProposalId)
                .containsExactlyInAnyOrder(likedByMe.getId(), likedByBoth.getId());
        assertThat(proposalService.getLikedProposalIds(9L, proposalIds))
                .containsExactlyInAnyOrder(likedByBoth.getId(), likedByOther.getId());
        assertThat(proposalService.getLikedProposalIds(10L, proposalIds)).isEmpty();
    }

    @Test
    @DisplayName("PostgreSQL은 음수 좋아요 수를 체크 제약으로 거부한다")
    void rejectsNegativeLikeCount() {
        Proposal saved = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
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

    @Test
    @DisplayName("PostgreSQL은 신규 제안을 PENDING으로 저장하고 세 상태를 enum 문자열로 저장·조회한다")
    void storesProposalStatus() {
        Proposal saved = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        proposalRepository.flush();
        entityManager.clear();
        assertThat(proposalRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(ProposalStatus.PENDING);

        for (ProposalStatus status : ProposalStatus.values()) {
            entityManager.createNativeQuery("update proposals set status = :status where id = :id")
                    .setParameter("status", status.name()).setParameter("id", saved.getId()).executeUpdate();
            entityManager.clear();
            assertThat(proposalRepository.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("PostgreSQL은 허용하지 않는 상태 값과 NULL 상태를 거부한다")
    void enforcesProposalStatusConstraint() {
        Proposal saved = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        proposalRepository.flush();

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("update proposals set status = 'DONE' where id = :id")
                    .setParameter("id", saved.getId()).executeUpdate();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("PostgreSQL은 NULL 상태를 거부한다")
    void rejectsNullStatus() {
        Proposal saved = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        proposalRepository.flush();

        assertThatThrownBy(() -> entityManager.createNativeQuery("update proposals set status = null where id = :id")
                .setParameter("id", saved.getId()).executeUpdate()).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("PostgreSQL은 상태 컬럼 없이 삽입한 기존 형태의 행을 PENDING으로 채운다")
    void defaultsLegacyRowsToPending() {
        entityManager.createNativeQuery("""
                insert into proposals (student_profile_id, owner_profile_id, title, customer_problem,
                    proposed_solution, work_plan, proposed_fee, draft_days, final_days, reference_image_urls)
                values (7, 5, 't', 'p', 's', 'w', 1000, 0, 1, '[]'::jsonb)
                """).executeUpdate();
        Object status = entityManager.createNativeQuery(
                "select status from proposals where title = 't' and student_profile_id = 7 order by id desc limit 1")
                .getSingleResult();
        assertThat(status).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("본인의 모든 상태 제안만 최신순·ID 내림차순으로 조회한다")
    void findsOnlyOwnProposalsNewestFirst() {
        Proposal first = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        Proposal second = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        Proposal other = proposalService.createProposal(command(List.of(1L), List.of()), 8L, null);
        proposalRepository.flush();

        assertThat(proposalRepository.findByStudentProfileIdOrderByCreatedAtDescIdDesc(7L))
                .extracting(Proposal::getId)
                .startsWith(second.getId()).contains(first.getId()).doesNotContain(other.getId());
    }

    @Test
    @DisplayName("받은 제안은 다른 사장님의 제안을 제외하고 모든 상태를 최신순·같은 시각은 ID 내림차순으로 조회한다")
    void findsOnlyReceivedProposalsNewestFirst() {
        // ID만으로 정렬하는 구현이 통과하지 못하도록 ID가 가장 작은 제안에 가장 최신 시각을, 가장 큰 제안에 가장 오래된 시각을 준다
        Proposal newest = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        Proposal sameTimeLowId = proposalService.createProposal(command(List.of(1L), List.of()), 8L, null);
        Proposal sameTimeHighId = proposalService.createProposal(command(List.of(1L), List.of()), 9L, null);
        Proposal older = proposalService.createProposal(command(List.of(1L), List.of()), 7L, null);
        Proposal otherOwner = proposalRepository.save(Proposal.create(
                7L, 6L, "제목", "문제", "해결", "계획", 1L, 0, 0, List.of(), null));
        proposalRepository.flush();
        entityManager.createNativeQuery("update proposals set created_at = :at where id in (:ids)")
                .setParameter("at", java.time.LocalDateTime.of(2030, 1, 1, 0, 0))
                .setParameter("ids", List.of(sameTimeLowId.getId(), sameTimeHighId.getId())).executeUpdate();
        entityManager.createNativeQuery("update proposals set created_at = :at where id = :id")
                .setParameter("at", java.time.LocalDateTime.of(2029, 1, 1, 0, 0))
                .setParameter("id", older.getId()).executeUpdate();
        entityManager.createNativeQuery("update proposals set created_at = :at where id = :id")
                .setParameter("at", java.time.LocalDateTime.of(2031, 1, 1, 0, 0))
                .setParameter("id", newest.getId()).executeUpdate();
        entityManager.createNativeQuery("update proposals set status = 'ACCEPTED' where id = :id")
                .setParameter("id", sameTimeLowId.getId()).executeUpdate();
        entityManager.createNativeQuery("update proposals set status = 'REJECTED' where id = :id")
                .setParameter("id", older.getId()).executeUpdate();
        entityManager.clear();

        List<Proposal> received = proposalRepository.findByOwnerProfileIdOrderByCreatedAtDescIdDesc(5L);

        assertThat(received).extracting(Proposal::getId)
                .contains(newest.getId(), sameTimeHighId.getId(), sameTimeLowId.getId(), older.getId())
                .doesNotContain(otherOwner.getId());
        assertThat(received).extracting(Proposal::getId).containsSubsequence(
                newest.getId(), sameTimeHighId.getId(), sameTimeLowId.getId(), older.getId());
        assertThat(received).extracting(Proposal::getStatus)
                .contains(ProposalStatus.PENDING, ProposalStatus.ACCEPTED, ProposalStatus.REJECTED);
    }
}
