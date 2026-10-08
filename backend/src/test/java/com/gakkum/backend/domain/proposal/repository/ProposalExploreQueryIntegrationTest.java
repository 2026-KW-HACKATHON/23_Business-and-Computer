package com.gakkum.backend.domain.proposal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetExploreProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalExploreOrder;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;

/**
 * PostgreSQL에서 탐색용 제안 쿼리의 정렬·커서 경계·대분류 조건과 취소·거절·본인 제안 제외를 확인한다. 각 테스트는 끝나면 롤백된다.
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
    private ProposalService proposalService;

    @Autowired
    private ProposalSpecialtyRepository proposalSpecialtyRepository;

    @Autowired
    private SpecialtyCategoryRepository specialtyCategoryRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private EntityManager entityManager;

    private Long categoryA;
    private Long specialtyA;
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
        specialtyA = specialtyRepository.save(Specialty.builder().specialtyCategoryId(categoryA).name("로고").build()).getId();
        Long a = specialtyA;
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
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtAndIdLessThanOrderByIdDesc(null, List.of(ProposalStatus.CANCELLED), null, T2, second, Limit.of(10))))
                .containsExactly(first);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtAndIdLessThanOrderByIdDesc(null, List.of(ProposalStatus.CANCELLED), null, T2, Long.MAX_VALUE, Limit.of(10))))
                .containsExactly(otherCategory, second, first);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(null, List.of(ProposalStatus.CANCELLED), null, T2, Limit.of(1))))
                .containsExactly(older);

        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(null, List.of(ProposalStatus.CANCELLED), null, T2, first, Limit.of(10))))
                .containsExactly(second, otherCategory);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(null, List.of(ProposalStatus.CANCELLED), null, BEFORE_T1, Limit.of(4))))
                .containsExactly(older, first, second, otherCategory);
    }

    @Test
    @DisplayName("대분류 없는 좋아요순 구간 쿼리는 같은 좋아요·같은 시각, 같은 좋아요·이전 시각, 더 적은 좋아요 순으로 나눠 읽는다")
    void readsLikeSegments() {
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndLikeCountAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, List.of(ProposalStatus.CANCELLED), null, LIKES + 3, T2, second, Limit.of(10)))).containsExactly(first);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndLikeCountAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                null, List.of(ProposalStatus.CANCELLED), null, LIKES + 3, T2, Limit.of(10)))).containsExactly(older);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndLikeCountLessThanOrderByLikeCountDescCreatedAtDescIdDesc(
                null, List.of(ProposalStatus.CANCELLED), null, LIKES + 5, Limit.of(3)))).containsExactly(second, first, older);
    }

    @Test
    @DisplayName("대분류 쿼리는 그 대분류 소분류가 연결된 제안만 튜플 경계 뒤부터 정렬 순서대로 읽는다")
    void readsCategoryQueries() {
        assertThat(ids(proposalRepository.findExploreLatestInCategory(
                null, List.of(ProposalStatus.CANCELLED), null, categoryA, AFTER_T2, Long.MAX_VALUE, Limit.of(10)))).containsExactly(second, first, older);
        assertThat(ids(proposalRepository.findExploreLatestInCategory(
                null, List.of(ProposalStatus.CANCELLED), null, categoryA, T2, second, Limit.of(10)))).containsExactly(first, older);
        assertThat(ids(proposalRepository.findExploreOldestInCategory(
                null, List.of(ProposalStatus.CANCELLED), null, categoryA, T1, older, Limit.of(10)))).containsExactly(first, second);
        assertThat(ids(proposalRepository.findExploreByLikesInCategory(
                null, List.of(ProposalStatus.CANCELLED), null, categoryA, LIKES + 3, T2, second, Limit.of(10)))).containsExactly(first, older);
        assertThat(proposalRepository.findExploreLatestInCategory(
                null, List.of(ProposalStatus.CANCELLED), null, Long.MAX_VALUE, AFTER_T2, Long.MAX_VALUE, Limit.of(10))).isEmpty();
    }

    @Test
    @DisplayName("취소된 제안은 모든 정렬의 구간 쿼리와 대분류 쿼리에서 빠지고 남은 제안이 limit을 채운다")
    void excludesCancelledProposalsFromEveryExploreQuery() {
        // second를 취소하면 T2 구간에는 first·otherCategory만, 대분류 A에는 first·older만 남는다
        entityManager.createNativeQuery("UPDATE proposals SET status = 'CANCELLED' WHERE id = :id")
                .setParameter("id", second).executeUpdate();
        entityManager.clear();
        List<ProposalStatus> cancelled = List.of(ProposalStatus.CANCELLED);

        // 최신순
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, cancelled, null, T2, Long.MAX_VALUE, Limit.of(10)))).containsExactly(otherCategory, first);
        // 취소된 행이 limit 자리를 차지하지 않는다
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, cancelled, null, T2, otherCategory, Limit.of(1)))).containsExactly(first);
        // 오래된순
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                null, cancelled, null, T2, first, Limit.of(10)))).containsExactly(otherCategory);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                null, cancelled, null, BEFORE_T1, Limit.of(3)))).containsExactly(older, first, otherCategory);
        // 좋아요순의 세 구간
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndLikeCountAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, cancelled, null, LIKES + 3, T2, Long.MAX_VALUE, Limit.of(10)))).containsExactly(first);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndLikeCountLessThanOrderByLikeCountDescCreatedAtDescIdDesc(
                null, cancelled, null, LIKES + 5, Limit.of(2)))).containsExactly(first, older);
        // 대분류 필터
        assertThat(ids(proposalRepository.findExploreLatestInCategory(
                null, cancelled, null, categoryA, AFTER_T2, Long.MAX_VALUE, Limit.of(2)))).containsExactly(first, older);
        assertThat(ids(proposalRepository.findExploreOldestInCategory(
                null, cancelled, null, categoryA, T1, older, Limit.of(10)))).containsExactly(first);
        assertThat(ids(proposalRepository.findExploreByLikesInCategory(
                null, cancelled, null, categoryA, LIKES + 3, AFTER_T2, Long.MAX_VALUE, Limit.of(2))))
                .containsExactly(first, older);
    }

    @Test
    @DisplayName("같은 좋아요·이전 시각 구간도 취소된 제안을 뺀다")
    void excludesCancelledProposalFromSameLikeEarlierSegment() {
        entityManager.createNativeQuery("UPDATE proposals SET status = 'CANCELLED' WHERE id = :id")
                .setParameter("id", older).executeUpdate();
        entityManager.clear();

        assertThat(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndLikeCountAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                null, List.of(ProposalStatus.CANCELLED), null, LIKES + 3, T2, Limit.of(10))).isEmpty();
        assertThat(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                null, List.of(ProposalStatus.CANCELLED), null, T2, Limit.of(1))).extracting(Proposal::getId).doesNotContain(older);
    }

    /**
     * first는 학생 8의 제안, second는 거절된 제안으로 바꾸고, 같은 시각·같은 좋아요의 third(학생 7, 대분류 A)를 더한다.
     * 학생 8에게는 T2에서 제외 대상 둘(first·second)이 ID 순으로 연속한다.
     * @return third의 ID
     */
    private Long givenRejectedAndOwnProposals() {
        Long third = proposal(specialtyA);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE proposals SET student_profile_id = 8 WHERE id = :id")
                .setParameter("id", first).executeUpdate();
        entityManager.createNativeQuery("UPDATE proposals SET status = 'REJECTED' WHERE id = :id")
                .setParameter("id", second).executeUpdate();
        entityManager.createNativeQuery(
                        "UPDATE proposals SET created_at = CAST(:t2 AS timestamp), like_count = :likes WHERE id = :id")
                .setParameter("t2", T2).setParameter("likes", LIKES + 3).setParameter("id", third).executeUpdate();
        entityManager.clear();
        return third;
    }

    @Test
    @DisplayName("거절된 제안과 조회 학생이 작성한 제안은 모든 정렬의 구간 쿼리와 대분류 쿼리에서 빠지고 남은 제안이 limit을 채운다")
    void excludesRejectedAndOwnProposalsFromEveryExploreQuery() {
        Long third = givenRejectedAndOwnProposals();
        List<ProposalStatus> excluded = List.of(ProposalStatus.CANCELLED, ProposalStatus.REJECTED);

        // 최신순: 제외된 first·second가 limit 자리를 차지하지 않는다
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, excluded, 8L, T2, Long.MAX_VALUE, Limit.of(10)))).containsExactly(third, otherCategory);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                null, excluded, 8L, AFTER_T2, Limit.of(3)))).containsExactly(third, otherCategory, older);
        // 오래된순
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                null, excluded, 8L, T2, Long.MIN_VALUE, Limit.of(1)))).containsExactly(otherCategory);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                null, excluded, 8L, BEFORE_T1, Limit.of(3)))).containsExactly(older, otherCategory, third);
        // 좋아요순의 세 구간
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndLikeCountAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, excluded, 8L, LIKES + 3, T2, third, Limit.of(10)))).isEmpty();
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndLikeCountAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                null, excluded, 8L, LIKES + 3, AFTER_T2, Limit.of(2)))).containsExactly(third, older);
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndLikeCountLessThanOrderByLikeCountDescCreatedAtDescIdDesc(
                null, excluded, 8L, LIKES + 5, Limit.of(2)))).containsExactly(third, older);
        // 대분류 필터
        assertThat(ids(proposalRepository.findExploreLatestInCategory(
                null, excluded, 8L, categoryA, AFTER_T2, Long.MAX_VALUE, Limit.of(2)))).containsExactly(third, older);
        assertThat(ids(proposalRepository.findExploreOldestInCategory(
                null, excluded, 8L, categoryA, T1, older, Limit.of(10)))).containsExactly(third);
        assertThat(ids(proposalRepository.findExploreByLikesInCategory(
                null, excluded, 8L, categoryA, LIKES + 3, AFTER_T2, Long.MAX_VALUE, Limit.of(2))))
                .containsExactly(third, older);

        // 다른 학생 7에게는 본인 제안(older·otherCategory·third)이 빠지고 학생 8의 first만 남는다
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                null, excluded, 7L, AFTER_T2, Limit.of(10)))).containsExactly(first);
        // 학생 프로필이 없으면 작성 학생으로 거르지 않고 거절된 제안만 뺀다
        assertThat(ids(proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                null, excluded, null, AFTER_T2, Limit.of(10)))).containsExactly(third, otherCategory, first, older);
        assertThat(ids(proposalRepository.findExploreLatestInCategory(
                null, excluded, null, categoryA, AFTER_T2, Long.MAX_VALUE, Limit.of(10))))
                .containsExactly(third, first, older);
    }

    @Test
    @DisplayName("제외 대상이 같은 생성 시각에 연속해 있어도 한 장씩 읽을 때 정렬과 대분류 유무마다 남은 제안이 중복·누락 없이 이어진다")
    void continuesAfterCursorWithoutRejectedAndOwnProposals() {
        Long third = givenRejectedAndOwnProposals();

        assertThat(readPages(null, ProposalExploreOrder.LATEST, 3, 8L)).containsExactly(third, otherCategory, older);
        assertThat(readPages(null, ProposalExploreOrder.OLDEST, 3, 8L)).containsExactly(older, otherCategory, third);
        assertThat(readPages(null, ProposalExploreOrder.LIKES, 3, 8L)).containsExactly(otherCategory, third, older);
        assertThat(readPages(categoryA, ProposalExploreOrder.LATEST, 3, 8L)).containsExactly(third, older);
        assertThat(readPages(categoryA, ProposalExploreOrder.OLDEST, 3, 8L)).containsExactly(older, third);
        assertThat(readPages(categoryA, ProposalExploreOrder.LIKES, 3, 8L)).containsExactly(third, older);
        // 학생 프로필이 없는 조회자는 거절된 second만 빠진 목록을 본다
        assertThat(readPages(null, ProposalExploreOrder.LATEST, 4, null))
                .containsExactly(third, otherCategory, first, older);
    }

    /** 탐색 화면용 조건으로 한 장씩, 커서를 마지막 행의 (likeCount, createdAt, id)로 옮기며 pages번 읽어 이어 붙인다. */
    private List<Long> readPages(Long categoryId, ProposalExploreOrder order, int pages, Long viewerStudentProfileId) {
        Integer likeCount = order == ProposalExploreOrder.LIKES ? Integer.MAX_VALUE : null;
        LocalDateTime createdAt = order == ProposalExploreOrder.OLDEST ? BEFORE_T1 : AFTER_T2;
        Long idBound = Long.MAX_VALUE;
        List<Long> read = new ArrayList<>();
        for (int page = 0; page < pages; page++) {
            List<ExploreProposalData> items = proposalService.getExploreProposals(GetExploreProposalsCommand.forViewer(
                    null, categoryId, order, likeCount, createdAt, idBound, 1, viewerStudentProfileId));
            if (items.isEmpty()) {
                break;
            }
            Proposal last = items.get(items.size() - 1).getProposal();
            likeCount = order == ProposalExploreOrder.LIKES ? last.getLikeCount() : null;
            createdAt = last.getCreatedAt();
            idBound = last.getId();
            read.add(last.getId());
        }
        return read;
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
