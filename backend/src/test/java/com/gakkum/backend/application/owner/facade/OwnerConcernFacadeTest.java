package com.gakkum.backend.application.owner.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.SaveStoreConcernCommand;
import com.gakkum.backend.domain.owner.dto.OwnerQueryDto.StoreConcernResult;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.entity.StoreConcern;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.owner.service.StoreConcernService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.transaction.ImmediateTransactionTemplate;

@DisplayName("사장님 파사드 - 가게 고민")
class OwnerConcernFacadeTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final Long OWNER_ID = 42L;

    private final UserService userService = mock(UserService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final StoreConcernService storeConcernService = mock(StoreConcernService.class);
    private final SpecialtyCategoryService specialtyCategoryService = mock(SpecialtyCategoryService.class);
    private final OwnerFacade facade = new OwnerFacade(userService, ownerService, mock(BusinessCategoryService.class),
            mock(JwtService.class), mock(JobService.class), mock(ProposalService.class), mock(AuthService.class),
            mock(MediaService.class), new ImmediateTransactionTemplate(), storeConcernService,
            specialtyCategoryService);

    @Test
    @DisplayName("고민을 저장하면 고른 분야가 있는지 확인하고 사장님 매장에 저장한 뒤 분야 이름을 붙여 돌려준다")
    void savesConcernWithCategory() {
        givenOwner();
        SaveStoreConcernCommand command = SaveStoreConcernCommand.of(USERNAME, "평일 점심 손님이 적어요", "설명", 5L);
        when(storeConcernService.saveConcern(OWNER_ID, command)).thenReturn(concern(5L));
        when(specialtyCategoryService.getCategoryNames(List.of(5L))).thenReturn(Map.of(5L, "디자인"));

        StoreConcernResult result = facade.saveConcern(command);

        verify(specialtyCategoryService).validateCategoryExists(5L);
        assertThat(result.getTitle()).isEqualTo("평일 점심 손님이 적어요");
        assertThat(result.getSpecialtyCategoryName()).isEqualTo("디자인");
    }

    @Test
    @DisplayName("분야를 고르지 않으면 분야를 확인·조회하지 않고 이름도 null이다")
    void savesConcernWithoutCategory() {
        givenOwner();
        SaveStoreConcernCommand command = SaveStoreConcernCommand.of(USERNAME, "메뉴판이 오래됐어요", null, null);
        when(storeConcernService.saveConcern(OWNER_ID, command)).thenReturn(concern(null));

        StoreConcernResult result = facade.saveConcern(command);

        assertThat(result.getSpecialtyCategoryId()).isNull();
        assertThat(result.getSpecialtyCategoryName()).isNull();
        verifyNoInteractions(specialtyCategoryService);
    }

    @Test
    @DisplayName("없는 분야를 고르면 SPECIALTY_CATEGORY_400으로 거부하고 저장하지 않는다")
    void rejectsUnknownCategory() {
        givenOwner();
        doThrow(new BusinessException(ErrorCode.SPECIALTY_CATEGORY_NOT_FOUND))
                .when(specialtyCategoryService).validateCategoryExists(99L);

        assertError(() -> facade.saveConcern(SaveStoreConcernCommand.of(USERNAME, "고민", null, 99L)),
                ErrorCode.SPECIALTY_CATEGORY_NOT_FOUND);
        verify(storeConcernService, never()).saveConcern(anyLong(), any());
    }

    @Test
    @DisplayName("해결되지 않은 고민이 없으면 빈 값, 있으면 그 고민을 돌려준다")
    void getsOpenConcern() {
        givenOwner();
        when(storeConcernService.findOpenConcern(OWNER_ID)).thenReturn(Optional.empty());
        assertThat(facade.getConcern(USERNAME)).isEmpty();

        when(storeConcernService.findOpenConcern(OWNER_ID)).thenReturn(Optional.of(concern(null)));
        assertThat(facade.getConcern(USERNAME)).get()
                .extracting(StoreConcernResult::getTitle).isEqualTo("평일 점심 손님이 적어요");
    }

    @Test
    @DisplayName("해결됐어요는 요청한 사장님 매장의 고민을 해결로 내린다")
    void resolvesOwnConcern() {
        givenOwner();

        facade.resolveConcern(USERNAME);

        verify(storeConcernService).resolveConcern(OWNER_ID);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = UserRole.class, names = {"STUDENT", "PENDING"})
    @DisplayName("사장님이 아니면 고민 조회·저장·해결을 OWNER_403_CONCERN으로 거부하고 고민을 건드리지 않는다")
    void rejectsNonOwner(UserRole role) {
        when(userService.getActiveUser(USERNAME)).thenReturn(user(role));

        assertError(() -> facade.getConcern(USERNAME), ErrorCode.OWNER_CONCERN_REQUIRED);
        assertError(() -> facade.saveConcern(SaveStoreConcernCommand.of(USERNAME, "고민", null, null)),
                ErrorCode.OWNER_CONCERN_REQUIRED);
        assertError(() -> facade.resolveConcern(USERNAME), ErrorCode.OWNER_CONCERN_REQUIRED);
        verifyNoInteractions(storeConcernService, ownerService);
    }

    private void givenOwner() {
        when(userService.getActiveUser(USERNAME)).thenReturn(user(UserRole.OWNER));
        when(ownerService.findOwnerProfileByUserId(USER_ID))
                .thenReturn(Optional.of(Owner.builder().id(OWNER_ID).userId(USER_ID).build()));
    }

    private User user(UserRole role) {
        return User.builder().id(USER_ID).username(USERNAME).role(role).isLock(false).build();
    }

    private StoreConcern concern(Long categoryId) {
        return StoreConcern.builder().id(7L).ownerProfileId(OWNER_ID).title("평일 점심 손님이 적어요")
                .specialtyCategoryId(categoryId).build();
    }

    private void assertError(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
