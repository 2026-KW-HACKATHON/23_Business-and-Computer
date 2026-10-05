package com.gakkum.backend.domain.proposal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;

/**
 * PostgreSQL에서 탐색용 제안 쿼리의 정렬·커서 경계·대분류 조건을 확인한다. 각 테스트는 끝나면 롤백된다.
 * 공유 DB의 다른 데이터와 섞이지 않도록 먼 미래 생성 시각과 아주 큰 좋아요 수를 쓰고, 대분류는 테스트마다 새로 만든다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ProposalExploreQueryIntegrationTest {

    private static final LocalDateTime BEFORE_T1 = LocalDateTime.of(2031, 1, 1, 0, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2031, 1, 1, 9, 0, 0, 123_456_000);
    private static final LocalDateTime T2 = LocalDateTime.of(2031, 1, 2, 9, 0, 0, 123_456_000);
    private static final LocalDateTime AFTER_T2 = LocalDateTime.of(2031, 1, 3, 0, 0);
    private static final int LIKES = 1_000_000;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private ProposalSpecialtyRepository proposalSpecialtyRepository;

    @Autowired
    private SpecialtyCategoryRepository specialtyCategoryRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private EntityManager entityManager;

    private Long categoryA;
    private Long older;
    private Long first;
    private Long second;
    private Long otherCategory;

    /**
     * older(T1, 좋아요 3, A), first(T2, 3, A), second(T2, 3, A), otherCategory(T2, 5, B).
     * first·second는 생성 시각과 좋아요가 같아 ID로만 순서가 갈린다.
     */
    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        categoryA = specialtyCategoryRepository.save(SpecialtyCategory.builder().name("탐색A-" + suffix).build()).getId();
        Long categoryB = specialtyCategoryRepository.save(SpecialtyCategory.builder().name("탐색B-" + suffix).build()).getId();
        Long a = specialtyRepository.save(Specialty.builder().specialtyCategoryId(categoryA).name("로고").build()).getId();
        Long b = specialtyRepository.save(Specialty.builder().specialtyCategoryId(categoryB).name("숏폼").build()).getId();

        older = proposal(a);
        first = proposal(a);
        second = proposal(a);
        otherCategory = proposal(b);
        entityManager.flush();
        entityManager.createNativeQuery("""
                UPDATE proposals
                SET created_at = CASE WHEN id = :older THEN CAST(:t1 AS timestamp) ELSE CAST(:t2 AS timestamp) END,
                    like_count = CASE WHEN id = :other THEN :likes + 5 ELSE :likes + 3 END
                WHERE id IN (:older, :first, :second, :other)
                """)
                .setParameter("t1", T1)
                .setParameter("t2", T2)
                .setParameter("likes", LIKES)
                .setParameter("older", older)
                .setParameter("first", first)
                .setParameter("second", second)
                .setParameter("other", otherCategory)
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    @DisplayName("대분류 없는 최신순·오래된순 구간 쿼리는 같은 시각 행을 경계 ID로 자르고 이전·이후 행을 시각·ID 순으로 읽는다")
    void readsCreatedAtSegments() {
        assertThat(ids(proposalRepository.findByDemoSessionIdAndCreatedAtAndIdLessThanOrderByIdDesc(null, T2, second, Limit.of(10))))
                .containsExactly(first);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndCreatedAtAndIdLessThanOrderByIdDesc(null, T2, Long.MAX_VALUE, Limit.of(10))))
                .containsExactly(otherCategory, second, first);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(null, T2, Limit.of(1))))
                .containsExactly(older);

        assertThat(ids(proposalRepository.findByDemoSessionIdAndCreatedAtAndIdGreaterThanOrderByIdAsc(null, T2, first, Limit.of(10))))
                .containsExactly(second, otherCategory);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(null, BEFORE_T1, Limit.of(4))))
                .containsExactly(older, first, second, otherCategory);
    }

    @Test
    @DisplayName("대분류 없는 좋아요순 구간 쿼리는 같은 좋아요·같은 시각, 같은 좋아요·이전 시각, 더 적은 좋아요 순으로 나눠 읽는다")
    void readsLikeSegments() {
        assertThat(ids(proposalRepository.findByDemoSessionIdAndLikeCountAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, LIKES + 3, T2, second, Limit.of(10)))).containsExactly(first);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndLikeCountAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                null, LIKES + 3, T2, Limit.of(10)))).containsExactly(older);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndLikeCountLessThanOrderByLikeCountDescCreatedAtDescIdDesc(
                null, LIKES + 5, Limit.of(3)))).containsExactly(second, first, older);
    }

    @Test
    @DisplayName("대분류 쿼리는 그 대분류 소분류가 연결된 제안만 튜플 경계 뒤부터 정렬 순서대로 읽는다")
    void readsCategoryQueries() {
        assertThat(ids(proposalRepository.findExploreLatestInCategory(
                null, categoryA, AFTER_T2, Long.MAX_VALUE, Limit.of(10)))).containsExactly(second, first, older);
        assertThat(ids(proposalRepository.findExploreLatestInCategory(
                null, categoryA, T2, second, Limit.of(10)))).containsExactly(first, older);
        assertThat(ids(proposalRepository.findExploreOldestInCategory(
                null, categoryA, T1, older, Limit.of(10)))).containsExactly(first, second);
        assertThat(ids(proposalRepository.findExploreByLikesInCategory(
                null, categoryA, LIKES + 3, T2, second, Limit.of(10)))).containsExactly(first, older);
        assertThat(proposalRepository.findExploreLatestInCategory(
                null, Long.MAX_VALUE, AFTER_T2, Long.MAX_VALUE, Limit.of(10))).isEmpty();
    }

    private Long proposal(Long specialtyId) {
        Proposal proposal = proposalRepository.save(Proposal.create(
                7L, 5L, "제안", "문제", "해결", "계획", 10000L, 0, 7, List.of(), null));
        proposalSpecialtyRepository.save(ProposalSpecialty.create(proposal.getId(), specialtyId));
        return proposal.getId();
    }

    private List<Long> ids(List<Proposal> proposals) {
        return proposals.stream().map(Proposal::getId).toList();
    }
}
