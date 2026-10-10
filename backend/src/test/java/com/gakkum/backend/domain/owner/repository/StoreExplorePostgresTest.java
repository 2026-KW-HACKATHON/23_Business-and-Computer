package com.gakkum.backend.domain.owner.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.gakkum.backend.application.explore.dto.ExploreQueryDto.StoreExploreResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.StoreItemResult;
import com.gakkum.backend.application.explore.dto.StoreExploreRequest;
import com.gakkum.backend.application.explore.dto.StoreExploreSort;
import com.gakkum.backend.application.explore.facade.ExploreFacade;
import com.gakkum.backend.domain.category.entity.BusinessCategory;
import com.gakkum.backend.domain.category.repository.BusinessCategoryRepository;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.domain.owner.service.StoreConcernService;

import jakarta.persistence.EntityManager;

/**
 * 튜플 비교 커서 경계는 PostgreSQL에서만 확인할 수 있어 실제 DB로 검증한다.
 * 공용 DB에 테스트 행을 쓰지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 트랜잭션은 테스트마다 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("매장 탐색 PostgreSQL 조회 (정렬·업종·커서 경계)")
class StoreExplorePostgresTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 29, 10, 0, 0, 123_456_000);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 9, 30, 10, 0, 0, 654_321_000);

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private BusinessCategoryRepository businessCategoryRepository;

    @Autowired
    private EntityManager entityManager;

    private ExploreFacade exploreFacade;

    @BeforeEach
    void setUp() {
        UserService userService = mock(UserService.class);
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id("01K58M6PJV8VAJMXHBHJ2PNB5C").username(USERNAME).role(UserRole.STUDENT)
                        .isLock(false).build());
        exploreFacade = new ExploreFacade(userService, mock(ProposalService.class), mock(JobService.class),
                new OwnerService(ownerRepository), mock(SpecialtyCategoryService.class),
                new BusinessCategoryService(businessCategoryRepository), mock(StudentService.class),
                mock(StoreConcernService.class));
    }

    @Test
    @DisplayName("같은 생성 시각의 매장을 최신순으로 여러 페이지 조회해도 중복·누락 없이 ID 내림차순으로 이어지고 마지막 페이지에서 끝난다")
    void pagesLatestThroughSameCreatedAt() {
        Long categoryId = category();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(store(categoryId, T2));
        }
        entityManager.clear();

        StoreExploreResult first = explore(StoreExploreSort.LATEST, categoryId, 2, null);
        StoreExploreResult second = explore(StoreExploreSort.LATEST, categoryId, 2, first.getNextCursor());
        StoreExploreResult third = explore(StoreExploreSort.LATEST, categoryId, 2, second.getNextCursor());

        assertThat(ids(first)).containsExactly(ids.get(4), ids.get(3));
        assertThat(ids(second)).containsExactly(ids.get(2), ids.get(1));
        assertThat(ids(third)).containsExactly(ids.get(0));
        assertThat(first.isHasNext()).isTrue();
        assertThat(second.isHasNext()).isTrue();
        assertThat(third.isHasNext()).isFalse();
        assertThat(third.getNextCursor()).isNull();
        assertThat(first.getItems().get(0).getCreatedAt()).isEqualTo(T2);
        assertThat(first.getItems().get(0).getBusinessCategory().getId()).isEqualTo(categoryId);
    }

    @Test
    @DisplayName("오래된순은 생성 시각 오름차순, 같은 시각은 ID 오름차순이고 개수가 size와 같으면 다음 페이지가 없다")
    void pagesOldestAcrossCreatedAt() {
        Long categoryId = category();
        Long late1 = store(categoryId, T2);
        Long early1 = store(categoryId, T1);
        Long late2 = store(categoryId, T2);
        Long early2 = store(categoryId, T1);
        entityManager.clear();

        StoreExploreResult first = explore(StoreExploreSort.OLDEST, categoryId, 3, null);
        StoreExploreResult second = explore(StoreExploreSort.OLDEST, categoryId, 3, first.getNextCursor());
        StoreExploreResult exact = explore(StoreExploreSort.OLDEST, categoryId, 4, null);

        assertThat(ids(first)).containsExactly(early1, early2, late1);
        assertThat(ids(second)).containsExactly(late2);
        assertThat(second.isHasNext()).isFalse();
        assertThat(ids(exact)).containsExactly(early1, early2, late1, late2);
        assertThat(exact.isHasNext()).isFalse();
    }

    @Test
    @DisplayName("업종 필터 없이 전체를 끝까지 넘겨도 다른 업종 매장까지 정렬 순서대로 한 번씩만 나온다")
    void pagesAllCategoriesWithoutDuplicates() {
        Long cafe = category();
        Long food = category();
        List<Long> mine = List.of(store(cafe, T1), store(food, T1), store(cafe, T2), store(food, T2), store(cafe, T2));
        entityManager.clear();

        for (StoreExploreSort sort : StoreExploreSort.values()) {
            List<StoreItemResult> all = new ArrayList<>();
            String cursor = null;
            do {
                StoreExploreResult page = explore(sort, null, 2, cursor);
                all.addAll(page.getItems());
                cursor = page.getNextCursor();
            } while (cursor != null);

            Comparator<StoreItemResult> order = Comparator.comparing(StoreItemResult::getCreatedAt)
                    .thenComparing(StoreItemResult::getOwnerProfileId);
            assertThat(all).isSortedAccordingTo(sort == StoreExploreSort.OLDEST ? order : order.reversed());
            assertThat(all).extracting(StoreItemResult::getOwnerProfileId).doesNotHaveDuplicates().containsAll(mine);
        }
    }

    @Test
    @DisplayName("업종 필터는 그 업종 매장만 고르고 매장이 없는 업종은 빈 목록이다")
    void filtersByCategory() {
        Long cafe = category();
        Long food = category();
        Long empty = category();
        Long cafeStore = store(cafe, T1);
        store(food, T2);
        entityManager.clear();

        assertThat(ids(explore(StoreExploreSort.LATEST, cafe, 20, null))).containsExactly(cafeStore);
        assertThat(ids(explore(StoreExploreSort.OLDEST, cafe, 20, null))).containsExactly(cafeStore);
        StoreExploreResult none = explore(StoreExploreSort.LATEST, empty, 20, null);
        assertThat(none.getItems()).isEmpty();
        assertThat(none.isHasNext()).isFalse();
    }

    private StoreExploreResult explore(StoreExploreSort sort, Long categoryId, int size, String cursor) {
        return exploreFacade.exploreStores(StoreExploreRequest.of(sort, categoryId, size, cursor).toCommand(USERNAME));
    }

    private List<Long> ids(StoreExploreResult result) {
        return result.getItems().stream().map(StoreItemResult::getOwnerProfileId).toList();
    }

    private Long category() {
        return businessCategoryRepository.saveAndFlush(
                BusinessCategory.builder().name("테스트 업종 " + UUID.randomUUID()).build()).getId();
    }

    // 생성 시각은 저장 시 자동으로 채워지므로 같은 시각을 만들려면 저장 뒤에 직접 고친다
    private Long store(Long categoryId, LocalDateTime createdAt) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        Long id = ownerRepository.saveAndFlush(Owner.create(unique.substring(0, 26), unique, null, null,
                "매장 " + unique.substring(0, 6), categoryId, null, null, null, null, null)).getId();
        entityManager.createNativeQuery("update owner_profiles set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate();
        return id;
    }
}
