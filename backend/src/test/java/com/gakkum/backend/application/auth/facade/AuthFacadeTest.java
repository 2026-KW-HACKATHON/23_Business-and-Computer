package com.gakkum.backend.application.auth.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.auth.dto.AuthCommandDto.VerifyOwnerBusinessCommand;
import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("인증 퍼사드")
class AuthFacadeTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String EMAIL = "student@kw.ac.kr";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final AuthFacade authFacade = new AuthFacade(
            new UserService(userRepository, mock(JwtService.class)), authService);

    @Test
    @DisplayName("가입 대기 사용자면 사용자 ID로 인증번호 발송을 맡긴다")
    void sendsVerificationWithPendingUserId() {
        givenUser(UserRole.PENDING);

        authFacade.sendStudentEmailVerification(USERNAME, EMAIL);

        verify(authService).sendStudentEmailVerification(USER_ID, EMAIL);
    }

    @Test
    @DisplayName("이미 사용 중인 이메일이면 DUPLICATE_EMAIL로 거부하고 인증번호를 발송하지 않는다")
    void rejectsDuplicateEmailBeforeSending() {
        givenUser(UserRole.PENDING);
        when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

        assertError(ErrorCode.DUPLICATE_EMAIL, () -> authFacade.sendStudentEmailVerification(USERNAME, EMAIL));
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("가입을 마친 사용자는 ALREADY_REGISTERED로 거부하고 인증번호를 발송·확인하지 않는다")
    void rejectsRegisteredUserForStudentEmail() {
        givenUser(UserRole.STUDENT);

        assertError(ErrorCode.ALREADY_REGISTERED, () -> authFacade.sendStudentEmailVerification(USERNAME, EMAIL));
        assertError(ErrorCode.ALREADY_REGISTERED, () -> authFacade.verifyStudentEmail(USERNAME, EMAIL, "123456"));
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("가입 대기 사용자면 사용자 ID로 인증번호 확인을 맡긴다")
    void verifiesCodeWithPendingUserId() {
        givenUser(UserRole.PENDING);

        authFacade.verifyStudentEmail(USERNAME, EMAIL, "123456");

        verify(authService).verifyStudentEmail(USER_ID, EMAIL, "123456");
    }

    @Test
    @DisplayName("인증번호 확인은 오입력 횟수가 저장되도록 BusinessException에 롤백하지 않는다")
    void verifyStudentEmailDoesNotRollbackOnBusinessException() throws Exception {
        Transactional transactional = AuthFacade.class
                .getMethod("verifyStudentEmail", String.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.noRollbackFor()).contains(BusinessException.class);
    }

    @Test
    @DisplayName("가입 대기 사용자는 사업자등록정보 진위 확인을 요청할 수 있다")
    void verifiesBusinessForPendingUser() {
        givenUser(UserRole.PENDING);
        VerifyOwnerBusinessCommand command = businessCommand();
        when(authService.verifyOwnerBusiness(command)).thenReturn(true);

        assertThat(authFacade.verifyOwnerBusiness(command)).isTrue();
    }

    @Test
    @DisplayName("가입 대기 사용자가 아니면 외부 사업자 확인을 호출하지 않는다")
    void rejectsNonPendingUserBeforeExternalCall() {
        givenUser(UserRole.OWNER);

        assertError(ErrorCode.ALREADY_REGISTERED, () -> authFacade.verifyOwnerBusiness(businessCommand()));
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("존재하지 않거나 잠긴 사용자는 UNAUTHORIZED로 거부하고 인증 서비스를 호출하지 않는다")
    void rejectsUnknownUser() {
        when(userRepository.findByUsernameAndIsLock(anyString(), any())).thenReturn(Optional.empty());

        assertError(ErrorCode.UNAUTHORIZED, () -> authFacade.verifyOwnerBusiness(businessCommand()));
        assertError(ErrorCode.UNAUTHORIZED, () -> authFacade.sendStudentEmailVerification(USERNAME, EMAIL));
        assertError(ErrorCode.UNAUTHORIZED, () -> authFacade.verifyStudentEmail(USERNAME, EMAIL, "123456"));
        verifyNoInteractions(authService);
    }

    private void givenUser(UserRole role) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(USER_ID).username(USERNAME).isLock(false).role(role).build()));
    }

    private VerifyOwnerBusinessCommand businessCommand() {
        return VerifyOwnerBusinessCommand.of(USERNAME, "김사장", LocalDate.of(2020, 3, 1), "1234567890");
    }

    private void assertError(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
