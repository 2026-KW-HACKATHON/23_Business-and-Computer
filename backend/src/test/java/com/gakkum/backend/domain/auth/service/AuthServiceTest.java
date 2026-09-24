package com.gakkum.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.gakkum.backend.domain.auth.client.NtsBusinessVerificationClient;
import com.gakkum.backend.domain.auth.dto.AuthCommandDto.VerifyOwnerBusinessCommand;
import com.gakkum.backend.domain.auth.entity.StudentEmailVerification;
import com.gakkum.backend.domain.auth.repository.StudentEmailVerificationRepository;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("인증 서비스")
class AuthServiceTest {

    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String EMAIL = "student@kw.ac.kr";
    private static final Instant START = Instant.parse("2026-09-24T00:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentEmailVerificationRepository verificationRepository =
            mock(StudentEmailVerificationRepository.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final NtsBusinessVerificationClient businessVerificationClient = mock(NtsBusinessVerificationClient.class);
    private final AtomicReference<StudentEmailVerification> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        User user = User.builder().id(USER_ID).username("KAKAO_12345")
                .isLock(false).role(UserRole.PENDING).build();
        when(userRepository.findByUsernameAndIsLockFalse("KAKAO_12345"))
                .thenReturn(Optional.of(user));
        when(userRepository.findByIdAndIsLockFalse(USER_ID))
                .thenReturn(Optional.of(user));
        when(verificationRepository.findById(USER_ID))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(verificationRepository.save(any(StudentEmailVerification.class)))
                .thenAnswer(invocation -> {
                    StudentEmailVerification verification = invocation.getArgument(0);
                    stored.set(verification);
                    return verification;
                });
        doAnswer(invocation -> {
            stored.set(null);
            return null;
        }).when(verificationRepository).delete(any(StudentEmailVerification.class));
    }

    @Test
    @DisplayName("인증번호를 해시로 저장하고 검증된 이메일은 가입 시 한 번만 소비한다")
    void sendsHashedCodeAndConsumesVerifiedEmailOnce() {
        serviceAt(START).sendStudentEmailVerification("KAKAO_12345", EMAIL);
        String code = sentCode();

        assertThat(stored.get().getCodeHash()).isNotEqualTo(code);
        assertThat(new BCryptPasswordEncoder().matches(code, stored.get().getCodeHash())).isTrue();

        serviceAt(START.plusSeconds(30)).verifyStudentEmail("KAKAO_12345", EMAIL, code);
        assertThat(stored.get().getVerifiedAt()).isEqualTo(START.plusSeconds(30));
        assertThat(stored.get().getCodeHash()).isNull();

        serviceAt(START.plusSeconds(60)).consumeVerifiedStudentEmail(USER_ID, EMAIL);
        assertThat(stored.get()).isNull();
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED,
                () -> serviceAt(START.plusSeconds(61)).consumeVerifiedStudentEmail(USER_ID, EMAIL));
    }

    @Test
    @DisplayName("60초 내 재발송을 거부하고 재발송 시 이전 인증번호를 무효화한다")
    void rejectsCooldownAndInvalidatesPreviousCodeOnResend() {
        serviceAt(START).sendStudentEmailVerification("KAKAO_12345", EMAIL);
        String oldCode = sentCode();

        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_COOLDOWN,
                () -> serviceAt(START.plusSeconds(59)).sendStudentEmailVerification("KAKAO_12345", EMAIL));

        serviceAt(START.plusSeconds(60)).sendStudentEmailVerification("KAKAO_12345", EMAIL);
        String newCode = sentCode();
        assertThat(newCode).isNotEqualTo(oldCode);
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_INVALID,
                () -> serviceAt(START.plusSeconds(61)).verifyStudentEmail("KAKAO_12345", EMAIL, oldCode));
        serviceAt(START.plusSeconds(61)).verifyStudentEmail("KAKAO_12345", EMAIL, newCode);
    }

    @Test
    @DisplayName("10분 만료 또는 오입력 5회 후 인증번호를 거부한다")
    void rejectsExpiredCodeAndFiveWrongAttempts() {
        serviceAt(START).sendStudentEmailVerification("KAKAO_12345", EMAIL);
        String code = sentCode();
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_INVALID,
                () -> serviceAt(START.plusSeconds(600)).verifyStudentEmail("KAKAO_12345", EMAIL, code));

        String wrongCode = code.equals("000000") ? "000001" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) {
            assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_INVALID,
                    () -> serviceAt(START.plusSeconds(1)).verifyStudentEmail("KAKAO_12345", EMAIL, wrongCode));
        }
        assertThat(stored.get().getFailedAttempts()).isEqualTo(5);
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_INVALID,
                () -> serviceAt(START.plusSeconds(2)).verifyStudentEmail("KAKAO_12345", EMAIL, code));
    }

    @Test
    @DisplayName("가입 시 미검증·다른 이메일·30분 지난 인증을 거부한다")
    void requiresMatchingEmailAndRecentVerificationForRegistration() {
        serviceAt(START).sendStudentEmailVerification("KAKAO_12345", EMAIL);
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED,
                () -> serviceAt(START).consumeVerifiedStudentEmail(USER_ID, EMAIL));

        serviceAt(START.plusSeconds(1)).verifyStudentEmail("KAKAO_12345", EMAIL, sentCode());
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED,
                () -> serviceAt(START.plusSeconds(2)).consumeVerifiedStudentEmail(USER_ID, "other@kw.ac.kr"));
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED,
                () -> serviceAt(START.plusSeconds(1801)).consumeVerifiedStudentEmail(USER_ID, EMAIL));
    }

    @Test
    @DisplayName("인증 후 재발송하면 기존 검증 상태를 초기화한다")
    void resendingAfterVerificationClearsApproval() {
        serviceAt(START).sendStudentEmailVerification("KAKAO_12345", EMAIL);
        serviceAt(START.plusSeconds(1)).verifyStudentEmail("KAKAO_12345", EMAIL, sentCode());

        serviceAt(START.plusSeconds(60)).sendStudentEmailVerification("KAKAO_12345", EMAIL);

        assertThat(stored.get().getVerifiedAt()).isNull();
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED,
                () -> serviceAt(START.plusSeconds(61)).consumeVerifiedStudentEmail(USER_ID, EMAIL));
    }

    @Test
    @DisplayName("메일 발송 실패 시 성공 처리하지 않는다")
    void doesNotReportSuccessWhenMailDeliveryFails() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertError(ErrorCode.STUDENT_EMAIL_DELIVERY_FAILED,
                () -> serviceAt(START).sendStudentEmailVerification("KAKAO_12345", EMAIL));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("이미 사용 중인 이메일에는 인증 메일을 보내지 않는다")
    void rejectsEmailAlreadyUsedByAnotherAccountBeforeSending() {
        when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

        assertError(ErrorCode.DUPLICATE_EMAIL,
                () -> serviceAt(START).sendStudentEmailVerification("KAKAO_12345", EMAIL));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("가입 대기 사용자는 사업자등록정보 진위 확인을 요청할 수 있다")
    void verifiesBusinessForPendingUser() {
        LocalDate openedAt = LocalDate.of(2020, 3, 1);
        when(businessVerificationClient.verify("1234567890", openedAt, "김사장")).thenReturn(true);

        VerifyOwnerBusinessCommand command = VerifyOwnerBusinessCommand.of(
                "KAKAO_12345", "김사장", openedAt, "1234567890");
        assertThat(serviceAt(START).verifyOwnerBusiness(command))
                .isTrue();
        verify(businessVerificationClient).verify("1234567890", openedAt, "김사장");
    }

    @Test
    @DisplayName("가입 대기 사용자가 아니면 외부 사업자 확인을 호출하지 않는다")
    void rejectsNonPendingUserBeforeExternalCall() {
        User owner = User.builder().id(USER_ID).username("KAKAO_12345")
                .isLock(false).role(UserRole.OWNER).build();
        when(userRepository.findByUsernameAndIsLockFalse("KAKAO_12345"))
                .thenReturn(Optional.of(owner));

        VerifyOwnerBusinessCommand command = VerifyOwnerBusinessCommand.of(
                "KAKAO_12345", "김사장", LocalDate.of(2020, 3, 1), "1234567890");
        assertError(ErrorCode.ALREADY_REGISTERED, () -> serviceAt(START).verifyOwnerBusiness(command));
        verify(businessVerificationClient, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 외부 사업자 확인을 호출하지 않는다")
    void rejectsUnknownUserBeforeExternalCall() {
        when(userRepository.findByUsernameAndIsLockFalse("KAKAO_12345"))
                .thenReturn(Optional.empty());

        VerifyOwnerBusinessCommand command = VerifyOwnerBusinessCommand.of(
                "KAKAO_12345", "김사장", LocalDate.of(2020, 3, 1), "1234567890");
        assertError(ErrorCode.UNAUTHORIZED, () -> serviceAt(START).verifyOwnerBusiness(command));
        verify(businessVerificationClient, never()).verify(any(), any(), any());
    }

    private AuthService serviceAt(Instant instant) {
        return new AuthService(userRepository, verificationRepository,
                mailSender, businessVerificationClient,
                Clock.fixed(instant, ZoneOffset.UTC), "sender@example.com");
    }

    private String sentCode() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        String body = captor.getAllValues().getLast().getText();
        return body.replaceAll("\\D", "").substring(0, 6);
    }

    private void assertError(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
