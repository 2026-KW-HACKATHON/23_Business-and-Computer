package com.gakkum.backend.application.jwt.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.gakkum.backend.application.jwt.dto.DevLoginRole;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("개발용 테스트 로그인")
class DevLoginFacadeTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    private final UserService userService = mock(UserService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final DevLoginFacade facade = new DevLoginFacade(userService, jwtService, KEY);

    @Test
    @DisplayName("사장님 역할은 테스트 사장님 계정의 사장님 토큰을 발급한다")
    void issuesOwnerToken() {
        when(userService.getActiveUser("DEV_OWNER")).thenReturn(user("DEV_OWNER", UserRole.OWNER));
        when(jwtService.issueAccessToken("DEV_OWNER", UserRole.OWNER)).thenReturn("owner-access-token");

        assertThat(facade.login(KEY, DevLoginRole.OWNER).accessToken()).isEqualTo("owner-access-token");
    }

    @Test
    @DisplayName("학생 역할은 테스트 학생 계정의 학생 토큰을 발급한다")
    void issuesStudentToken() {
        when(userService.getActiveUser("DEV_STUDENT")).thenReturn(user("DEV_STUDENT", UserRole.STUDENT));
        when(jwtService.issueAccessToken("DEV_STUDENT", UserRole.STUDENT)).thenReturn("student-access-token");

        assertThat(facade.login(KEY, DevLoginRole.STUDENT).accessToken()).isEqualTo("student-access-token");
    }

    @Test
    @DisplayName("키가 없거나 다르면 계정을 조회하지 않고 인증 오류로 거부한다")
    void rejectsMissingOrWrongKey() {
        assertUnauthorized(() -> facade.login(null, DevLoginRole.OWNER));
        assertUnauthorized(() -> facade.login("", DevLoginRole.OWNER));
        assertUnauthorized(() -> facade.login(KEY.toUpperCase(), DevLoginRole.OWNER));

        verifyNoInteractions(userService, jwtService);
    }

    @Test
    @DisplayName("테스트 계정이 없거나 잠겨 있으면 토큰을 발급하지 않는다")
    void rejectsWhenAccountIsUnavailable() {
        when(userService.getActiveUser("DEV_OWNER")).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertUnauthorized(() -> facade.login(KEY, DevLoginRole.OWNER));

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("테스트 계정의 역할이 요청한 역할과 다르면 토큰을 발급하지 않는다")
    void rejectsWhenAccountRoleDiffers() {
        when(userService.getActiveUser("DEV_OWNER")).thenReturn(user("DEV_OWNER", UserRole.PENDING));

        assertUnauthorized(() -> facade.login(KEY, DevLoginRole.OWNER));

        verifyNoInteractions(jwtService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0123456789abcdef0123456789abcde"})
    @DisplayName("공유 키가 비었거나 32자보다 짧으면 생성 단계에서 실패한다")
    void rejectsShortKeyAtConstruction(String key) {
        assertThatThrownBy(() -> new DevLoginFacade(userService, jwtService, key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dev-login.key");
    }

    private void assertUnauthorized(Runnable login) {
        assertThatThrownBy(login::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private User user(String username, UserRole role) {
        return User.builder()
                .id("01K6DEV0000000000000000001")
                .username(username)
                .isLock(false)
                .role(role)
                .build();
    }
}
