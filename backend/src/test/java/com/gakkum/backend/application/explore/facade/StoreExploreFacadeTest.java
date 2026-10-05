package com.gakkum.backend.application.explore.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.application.explore.dto.ExploreCommandDto.StoreExploreCommand;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.StoreExploreResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.StoreItemResult;
import com.gakkum.backend.application.explore.dto.StoreExploreCursor;
import com.gakkum.backend.application.explore.dto.StoreExploreRequest;
import com.gakkum.backend.application.explore.dto.StoreExploreSort;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.GetExploreStoresCommand;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("탐색 파사드 - 매장 목록")
class StoreExploreFacadeTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 29, 10, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 9, 30, 10, 0);

    private final UserService userService = mock(UserService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final BusinessCategoryService businessCategoryService = mock(BusinessCategoryService.class);
    private final ExploreFacade exploreFacade = new ExploreFacade(userService, mock(ProposalService.class),
            mock(JobService.class), ownerService, mock(SpecialtyCategoryService.class), businessCategoryService,
            mock(StudentService.class));

    @Test
    @DisplayName("학생은 size+1개를 읽어 size개만 받고, 남은 한 개로 다음 페이지를 판단해 마지막 매장으로 커서를 만든다")
    void returnsPageWithNextCursor() {
        givenUser(UserRole.STUDENT);
        when(ownerService.getExploreStores(any())).thenReturn(List.of(
                store(9L, T2, 3L), store(8L, T2, 2L), store(7L, T1, 3L)));
        when(businessCategoryService.getCategoryNames(List.of(3L, 2L)))
                .thenReturn(Map.of(3L, "카페", 2L, "음식점"));

        StoreExploreResult result = exploreFacade.exploreStores(command(StoreExploreSort.LATEST, null, 2, null));

        assertThat(result.getItems()).extracting(StoreItemResult::getOwnerProfileId).containsExactly(9L, 8L);
        StoreItemResult first = result.getItems().get(0);
        assertThat(first.getStoreName()).isEqualTo("매장 9");
        assertThat(first.getProfileImageUrl()).isEqualTo("https://example.com/9.jpg");
        assertThat(first.getStoreAddress()).isEqualTo("주소 9");
        assertThat(first.getCreatedAt()).isEqualTo(T2);
        assertThat(first.getBusinessCategory().getId()).isEqualTo(3L);
        assertThat(first.getBusinessCategory().getName()).isEqualTo("카페");
        assertThat(result.getItems().get(1).getBusinessCategory().getName()).isEqualTo("음식점");
        assertThat(result.isHasNext()).isTrue();
        StoreExploreCursor next = StoreExploreCursor.decode(result.getNextCursor());
        assertThat(next.matches(StoreExploreSort.LATEST, null)).isTrue();
        assertThat(next.getCreatedAt()).isEqualTo(T2);
        assertThat(next.getId()).isEqualTo(8L);

        GetExploreStoresCommand query = captureQuery();
        assertThat(query.getBusinessCategoryId()).isNull();
        assertThat(query.isOldestFirst()).isFalse();
        assertThat(query.getLimit()).isEqualTo(3);
        assertThat(query.getCreatedAtBound()).isAfter(T2);
        assertThat(query.getIdBound()).isEqualTo(Long.MAX_VALUE);
        // 이번 페이지에 없는 매장의 업종은 조회하지 않고, 업종 이름은 한 번에 조회한다
        verify(businessCategoryService).getCategoryNames(List.of(3L, 2L));
        verify(businessCategoryService, never()).validateCategoryExists(any());
    }

    @Test
    @DisplayName("마지막 페이지는 다음 커서 없이 반환하고 사진·주소가 없으면 null로 둔다")
    void returnsLastPage() {
        givenUser(UserRole.STUDENT);
        when(ownerService.getExploreStores(any())).thenReturn(List.of(
                Owner.builder().id(7L).storeName("매장 7").categoryId(3L).createdAt(T1).build()));
        when(businessCategoryService.getCategoryNames(List.of(3L))).thenReturn(Map.of(3L, "카페"));

        StoreExploreResult result = exploreFacade.exploreStores(command(StoreExploreSort.LATEST, null, 1, null));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getProfileImageUrl()).isNull();
        assertThat(result.getItems().get(0).getStoreAddress()).isNull();
        assertThat(result.isHasNext()).isFalse();
        assertThat(result.getNextCursor()).isNull();
    }

    @Test
    @DisplayName("오래된순 첫 페이지는 가장 이른 경계에서 시작하고 업종 필터를 넘긴다")
    void startsOldestFromEarliestBound() {
        givenUser(UserRole.STUDENT);

        exploreFacade.exploreStores(command(StoreExploreSort.OLDEST, 3L, 20, null));

        GetExploreStoresCommand query = captureQuery();
        assertThat(query.getBusinessCategoryId()).isEqualTo(3L);
        assertThat(query.isOldestFirst()).isTrue();
        assertThat(query.getLimit()).isEqualTo(21);
        assertThat(query.getCreatedAtBound()).isBefore(T1);
        assertThat(query.getIdBound()).isEqualTo(Long.MIN_VALUE);
        verify(businessCategoryService).validateCategoryExists(3L);
    }

    @Test
    @DisplayName("커서가 있으면 커서의 생성 시각과 ID를 경계로 넘긴다")
    void readsAfterCursor() {
        givenUser(UserRole.STUDENT);
        String cursor = StoreExploreCursor.of(StoreExploreSort.OLDEST, 3L, T1, 7L).encode();

        exploreFacade.exploreStores(command(StoreExploreSort.OLDEST, 3L, 20, cursor));

        GetExploreStoresCommand query = captureQuery();
        assertThat(query.getCreatedAtBound()).isEqualTo(T1);
        assertThat(query.getIdBound()).isEqualTo(7L);
    }

    @Test
    @DisplayName("존재하지만 매장이 없는 업종은 빈 목록을 반환한다")
    void returnsEmptyForCategoryWithoutStores() {
        givenUser(UserRole.STUDENT);
        when(ownerService.getExploreStores(any())).thenReturn(List.of());

        StoreExploreResult result = exploreFacade.exploreStores(command(StoreExploreSort.LATEST, 3L, 20, null));

        assertThat(result.getItems()).isEmpty();
        assertThat(result.isHasNext()).isFalse();
        assertThat(result.getNextCursor()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = UserRole.class, names = {"OWNER", "PENDING"})
    @DisplayName("사장님·가입 대기 사용자는 STORE_403_STUDENT로 거부하고 매장을 조회하지 않는다")
    void rejectsNonStudent(UserRole role) {
        givenUser(role);

        assertError(command(StoreExploreSort.LATEST, 3L, 20, null), ErrorCode.STORE_STUDENT_REQUIRED);
        verifyNoInteractions(ownerService, businessCategoryService);
    }

    @Test
    @DisplayName("활성 사용자를 찾지 못하면 COMMON_401로 거부한다")
    void rejectsInactiveUser() {
        when(userService.getActiveUser(USERNAME)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertError(command(StoreExploreSort.LATEST, null, 20, null), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(ownerService);
    }

    @Test
    @DisplayName("필터로 지정한 업종이 없으면 CATEGORY_400으로 거부하고 매장을 조회하지 않는다")
    void rejectsUnknownCategoryFilter() {
        givenUser(UserRole.STUDENT);
        doThrow(new BusinessException(ErrorCode.BUSINESS_CATEGORY_NOT_FOUND))
                .when(businessCategoryService).validateCategoryExists(99L);

        assertError(command(StoreExploreSort.LATEST, 99L, 20, null), ErrorCode.BUSINESS_CATEGORY_NOT_FOUND);
        verifyNoInteractions(ownerService);
    }

    @Test
    @DisplayName("매장이 참조하는 업종이 누락되면 COMMON_500을 그대로 전달한다")
    void propagatesMissingCategoryReference() {
        givenUser(UserRole.STUDENT);
        when(ownerService.getExploreStores(any())).thenReturn(List.of(store(9L, T2, 77L)));
        when(businessCategoryService.getCategoryNames(List.of(77L)))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        assertError(command(StoreExploreSort.LATEST, null, 20, null), ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private void assertError(StoreExploreCommand command, ErrorCode errorCode) {
        assertThatThrownBy(() -> exploreFacade.exploreStores(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private void givenUser(UserRole role) {
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id("01K58M6PJV8VAJMXHBHJ2PNB5C").username(USERNAME).role(role).isLock(false).build());
    }

    private GetExploreStoresCommand captureQuery() {
        ArgumentCaptor<GetExploreStoresCommand> captor = ArgumentCaptor.forClass(GetExploreStoresCommand.class);
        verify(ownerService).getExploreStores(captor.capture());
        return captor.getValue();
    }

    private StoreExploreCommand command(StoreExploreSort sort, Long businessCategoryId, int size, String cursor) {
        return StoreExploreRequest.of(sort, businessCategoryId, size, cursor).toCommand(USERNAME);
    }

    private Owner store(Long id, LocalDateTime createdAt, Long categoryId) {
        return Owner.builder()
                .id(id)
                .storeName("매장 " + id)
                .categoryId(categoryId)
                .profileImageUrl("https://example.com/" + id + ".jpg")
                .storeAddress("주소 " + id)
                .createdAt(createdAt)
                .build();
    }

    @Test
    @DisplayName("매장 탐색은 조회한 학생의 격리 범위를 넘겨 실제 학생은 null, 데모 학생은 자기 데모 세션 ID로 조회한다")
    void passesViewerDemoSessionToStoreQuery() {
        givenUser(UserRole.STUDENT);
        exploreFacade.exploreStores(command(StoreExploreSort.LATEST, null, 20, null));
        assertThat(captureQuery().getDemoSessionId()).isNull();

        org.mockito.Mockito.clearInvocations(ownerService);
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder()
                .id("01K58M6PJV8VAJMXHBHJ2PNB5C").username(USERNAME).role(UserRole.STUDENT).isLock(false)
                .demoSessionId("01K6DEMO00000000000000000A").build());
        exploreFacade.exploreStores(command(StoreExploreSort.OLDEST, null, 20, null));
        assertThat(captureQuery().getDemoSessionId()).isEqualTo("01K6DEMO00000000000000000A");
    }
}
