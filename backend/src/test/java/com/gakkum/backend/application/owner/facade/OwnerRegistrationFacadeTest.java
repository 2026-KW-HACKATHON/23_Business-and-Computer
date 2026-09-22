package com.gakkum.backend.application.owner.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.gakkum.backend.application.owner.dto.OwnerRegistrationCommand;
import com.gakkum.backend.application.owner.dto.OwnerRegistrationResponse;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class OwnerRegistrationFacadeTest {

    private final UserService userService = mock(UserService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final BusinessCategoryService businessCategoryService = mock(BusinessCategoryService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final OwnerRegistrationFacade facade = new OwnerRegistrationFacade(
            userService,
            ownerService,
            businessCategoryService,
            jwtService);

    private final User user = User.builder()
            .id("01K58M6PJV8VAJMXHBHJ2PNB5C")
            .username("KAKAO_12345")
            .isLock(false)
            .role(UserRole.PENDING)
            .build();

    private final OwnerRegistrationCommand command = OwnerRegistrationCommand.of(
            "김사장",
            "치킨플러스",
            "서울시 월계1동 광운로23",
            2L,
            "12341453312",
            LocalDate.of(2020, 3, 1),
            "김사장",
            "매장 한 줄 소개",
            List.of("https://image.example.com/store1.png", "https://image.example.com/store2.png"),
            "https://image.example.com/profile.png");

    @Test
    void coordinatesOwnerRegistration() {
        when(userService.validateOwnerRegistration("KAKAO_12345")).thenReturn(user);
        when(userService.completeOwnerRegistration(user, "김사장")).thenReturn(user);
        when(jwtService.issueAccessToken("KAKAO_12345", UserRole.OWNER)).thenReturn("access-token");
        when(jwtService.replaceRefreshToken("KAKAO_12345", UserRole.OWNER)).thenReturn("refresh-token");

        OwnerRegistrationResponse response = facade.register("KAKAO_12345", command);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");

        InOrder order = inOrder(userService, ownerService, businessCategoryService, jwtService);
        order.verify(userService).validateOwnerRegistration("KAKAO_12345");
        order.verify(ownerService).validateBusinessNumberAvailable("12341453312");
        order.verify(businessCategoryService).validateCategoryExists(2L);
        order.verify(userService).completeOwnerRegistration(user, "김사장");

        ArgumentCaptor<CreateOwnerProfileCommand> ownerCaptor =
                ArgumentCaptor.forClass(CreateOwnerProfileCommand.class);
        order.verify(ownerService).createOwnerProfile(ownerCaptor.capture());
        CreateOwnerProfileCommand ownerCommand = ownerCaptor.getValue();
        assertThat(ownerCommand.getUserId()).isEqualTo("01K58M6PJV8VAJMXHBHJ2PNB5C");
        assertThat(ownerCommand.getBusinessNumber()).isEqualTo("12341453312");
        assertThat(ownerCommand.getOpenedAt()).isEqualTo(LocalDate.of(2020, 3, 1));
        assertThat(ownerCommand.getRepresentativeName()).isEqualTo("김사장");
        assertThat(ownerCommand.getStoreName()).isEqualTo("치킨플러스");
        assertThat(ownerCommand.getCategoryId()).isEqualTo(2L);
        assertThat(ownerCommand.getStoreAddress()).isEqualTo("서울시 월계1동 광운로23");
        assertThat(ownerCommand.getDescription()).isEqualTo("매장 한 줄 소개");
        assertThat(ownerCommand.getProfileImageUrl()).isEqualTo("https://image.example.com/profile.png");
        assertThat(ownerCommand.getStoreImageUrls())
                .containsExactly("https://image.example.com/store1.png", "https://image.example.com/store2.png");

        order.verify(jwtService).issueAccessToken("KAKAO_12345", UserRole.OWNER);
        order.verify(jwtService).replaceRefreshToken("KAKAO_12345", UserRole.OWNER);
    }

    @Test
    void stopsImmediatelyWhenUserIsAlreadyRegistered() {
        when(userService.validateOwnerRegistration("KAKAO_12345"))
                .thenThrow(new BusinessException(ErrorCode.ALREADY_REGISTERED));

        assertThatThrownBy(() -> facade.register("KAKAO_12345", command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_REGISTERED));

        verify(userService, never()).completeOwnerRegistration(any(), any());
        verifyNoInteractions(ownerService, businessCategoryService, jwtService);
    }

    @Test
    void stopsBeforeCategoryCheckWhenBusinessNumberIsDuplicated() {
        when(userService.validateOwnerRegistration("KAKAO_12345")).thenReturn(user);
        doThrow(new BusinessException(ErrorCode.DUPLICATE_BUSINESS_NUMBER))
                .when(ownerService).validateBusinessNumberAvailable("12341453312");

        assertThatThrownBy(() -> facade.register("KAKAO_12345", command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BUSINESS_NUMBER));

        verify(userService, never()).completeOwnerRegistration(any(), any());
        verify(ownerService, never()).createOwnerProfile(any());
        verifyNoInteractions(businessCategoryService, jwtService);
    }

    @Test
    void stopsBeforeSavingWhenCategoryDoesNotExist() {
        when(userService.validateOwnerRegistration("KAKAO_12345")).thenReturn(user);
        doThrow(new BusinessException(ErrorCode.BUSINESS_CATEGORY_NOT_FOUND))
                .when(businessCategoryService).validateCategoryExists(2L);

        assertThatThrownBy(() -> facade.register("KAKAO_12345", command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_CATEGORY_NOT_FOUND));

        verify(userService, never()).completeOwnerRegistration(any(), any());
        verify(ownerService, never()).createOwnerProfile(any());
        verifyNoInteractions(jwtService);
    }
}
