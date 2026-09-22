package com.gakkum.backend.domain.owner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class OwnerServiceTest {

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

        Owner savedOwner = ownerService.createOwnerProfile(command);

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
}
