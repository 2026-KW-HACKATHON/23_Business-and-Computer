package com.gakkum.backend.domain.owner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.GetExploreStoresCommand;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class OwnerServiceTest {

    private static final String SESSION_A = "01K6DEMO00000000000000000A";
    private static final String SESSION_B = "01K6DEMO00000000000000000B";

    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final OwnerService ownerService = new OwnerService(ownerRepository);

    @Test
    void createsOwnerProfile() {
        when(ownerRepository.save(any(Owner.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOwnerProfileCommand command = CreateOwnerProfileCommand.of(
                "01K58M6PJV8VAJMXHBHJ2PNB5C",
                "12341453312",
                LocalDate.of(2020, 3, 1),
                "김사장",
                "치킨플러스",
                2L,
                "서울시 월계1동 광운로23",
                "매장 한 줄 소개",
                "https://image.example.com/profile.png",
                List.of("https://image.example.com/store1.png", "https://image.example.com/store2.png"));

        Owner savedOwner = ownerService.createOwnerProfile(command, null);

        ArgumentCaptor<Owner> ownerCaptor = ArgumentCaptor.forClass(Owner.class);
        verify(ownerRepository).save(ownerCaptor.capture());
        assertThat(savedOwner).isSameAs(ownerCaptor.getValue());
        assertThat(savedOwner.getUserId()).isEqualTo("01K58M6PJV8VAJMXHBHJ2PNB5C");
        assertThat(savedOwner.getBusinessNumber()).isEqualTo("12341453312");
        assertThat(savedOwner.getOpenedAt()).isEqualTo(LocalDate.of(2020, 3, 1));
        assertThat(savedOwner.getRepresentativeName()).isEqualTo("김사장");
        assertThat(savedOwner.getStoreName()).isEqualTo("치킨플러스");
        assertThat(savedOwner.getCategoryId()).isEqualTo(2L);
        assertThat(savedOwner.getStoreAddress()).isEqualTo("서울시 월계1동 광운로23");
        assertThat(savedOwner.getDescription()).isEqualTo("매장 한 줄 소개");
        assertThat(savedOwner.getProfileImageUrl()).isEqualTo("https://image.example.com/profile.png");
        assertThat(savedOwner.getStoreImageUrls())
                .containsExactly("https://image.example.com/store1.png", "https://image.example.com/store2.png");
    }

    @Test
    void rejectsDuplicateBusinessNumber() {
        when(ownerRepository.existsByBusinessNumber("12341453312")).thenReturn(true);

        assertThatThrownBy(() -> ownerService.validateBusinessNumberAvailable("12341453312"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BUSINESS_NUMBER));
    }

    @Test
    @DisplayName("지정한 사장님 프로필이 있으면 통과하고 없으면 OWNER_404로 거부한다")
    void validatesOwnerProfileExists() {
        when(ownerRepository.findById(5L)).thenReturn(Optional.of(Owner.builder().id(5L).build()));
        when(ownerRepository.findById(6L)).thenReturn(Optional.empty());

        ownerService.validateOwnerProfileExists(5L, null);
        assertOwnerNotFound(6L, null);
    }

    @Test
    @DisplayName("사장님 프로필은 요청자와 격리 범위가 같을 때만 있는 것으로 보고 다르면 OWNER_404로 거부한다")
    void validatesOwnerProfileWithinSameDemoSession() {
        when(ownerRepository.findById(5L)).thenReturn(Optional.of(Owner.builder().id(5L).build()));
        when(ownerRepository.findById(7L))
                .thenReturn(Optional.of(Owner.builder().id(7L).demoSessionId(SESSION_A).build()));

        ownerService.validateOwnerProfileExists(7L, SESSION_A);
        assertOwnerNotFound(7L, null);
        assertOwnerNotFound(7L, SESSION_B);
        assertOwnerNotFound(5L, SESSION_A);
    }

    private void assertOwnerNotFound(Long ownerProfileId, String demoSessionId) {
        assertThatThrownBy(() -> ownerService.validateOwnerProfileExists(ownerProfileId, demoSessionId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OWNER_NOT_FOUND));
    }

    @Test
    @DisplayName("탐색용 매장은 정렬 방향과 업종 유무에 맞는 쿼리 하나로 경계와 개수를 넘겨 읽는다")
    void readsExploreStoresWithMatchingQuery() {
        LocalDateTime bound = LocalDateTime.of(2026, 9, 30, 10, 0);
        Owner store = Owner.builder().id(7L).storeName("가꿈 카페").build();
        when(ownerRepository.findExploreLatest(null, bound, 9L, Limit.of(21))).thenReturn(List.of(store));

        assertThat(ownerService.getExploreStores(GetExploreStoresCommand.of(null, null, false, bound, 9L, 21)))
                .containsExactly(store);
        ownerService.getExploreStores(GetExploreStoresCommand.of(null, null, true, bound, 9L, 21));
        ownerService.getExploreStores(GetExploreStoresCommand.of(null, 3L, false, bound, 9L, 21));
        ownerService.getExploreStores(GetExploreStoresCommand.of(null, 3L, true, bound, 9L, 21));

        verify(ownerRepository).findExploreOldest(null, bound, 9L, Limit.of(21));
        verify(ownerRepository).findExploreLatestInCategory(null, 3L, bound, 9L, Limit.of(21));
        verify(ownerRepository).findExploreOldestInCategory(null, 3L, bound, 9L, Limit.of(21));
    }

    @Test
    @DisplayName("사장님 프로필을 ID별 Map으로 한 번에 읽고 하나라도 없으면 500으로 거부한다")
    void readsOwnerProfilesByIds() {
        Owner owner = Owner.builder().id(5L).storeName("가꿈 카페").build();
        when(ownerRepository.findAllById(java.util.Set.of(5L, 6L))).thenReturn(List.of(owner));
        when(ownerRepository.findAllById(java.util.Set.of(5L))).thenReturn(List.of(owner));

        assertThat(ownerService.getOwnerProfilesByIds(java.util.Set.of(5L))).containsEntry(5L, owner);
        assertThat(ownerService.getOwnerProfilesByIds(java.util.Set.of())).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> ownerService.getOwnerProfilesByIds(java.util.Set.of(5L, 6L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
