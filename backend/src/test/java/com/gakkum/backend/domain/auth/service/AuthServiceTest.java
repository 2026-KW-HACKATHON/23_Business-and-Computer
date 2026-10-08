package com.gakkum.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import com.gakkum.backend.domain.auth.client.NtsBusinessVerificationClient;
import com.gakkum.backend.domain.auth.dto.AuthCommandDto.VerifyOwnerBusinessCommand;
import com.gakkum.backend.domain.auth.entity.StudentEmailVerification;
import com.gakkum.backend.domain.auth.repository.StudentEmailVerificationRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("인증 서비스")
class AuthServiceTest {

    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String EMAIL = "student@kw.ac.kr";
    private static final Instant START = Instant.parse("2026-09-24T00:00:00Z");

    private final StudentEmailVerificationRepository verificationRepository =
            mock(StudentEmailVerificationRepository.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final NtsBusinessVerificationClient businessVerificationClient = mock(NtsBusinessVerificationClient.class);
    private final AtomicReference<StudentEmailVerification> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
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
        serviceAt(START).sendStudentEmailVerification(USER_ID, EMAIL);
        String code = sentCode();

        assertThat(stored.get().getCodeHash()).isNotEqualTo(code);
        assertThat(new BCryptPasswordEncoder().matches(code, stored.get().getCodeHash())).isTrue();

        serviceAt(START.plusSeconds(30)).verifyStudentEmail(USER_ID, EMAIL, code);
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
        serviceAt(START).sendStudentEmailVerification(USER_ID, EMAIL);
        String oldCode = sentCode();

        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_COOLDOWN,
                () -> serviceAt(START.plusSeconds(59)).sendStudentEmailVerification(USER_ID, EMAIL));

        serviceAt(START.plusSeconds(60)).sendStudentEmailVerification(USER_ID, EMAIL);
        String newCode = sentCode();
        assertThat(newCode).isNotEqualTo(oldCode);
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_INVALID,
                () -> serviceAt(START.plusSeconds(61)).verifyStudentEmail(USER_ID, EMAIL, oldCode));
        serviceAt(START.plusSeconds(61)).verifyStudentEmail(USER_ID, EMAIL, newCode);
    }

    @Test
    @DisplayName("10분 만료 또는 오입력 5회 후 인증번호를 거부한다")
    void rejectsExpiredCodeAndFiveWrongAttempts() {
        serviceAt(START).sendStudentEmailVerification(USER_ID, EMAIL);
        String code = sentCode();
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_INVALID,
                () -> serviceAt(START.plusSeconds(600)).verifyStudentEmail(USER_ID, EMAIL, code));

        String wrongCode = code.equals("000000") ? "000001" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) {
            assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_INVALID,
                    () -> serviceAt(START.plusSeconds(1)).verifyStudentEmail(USER_ID, EMAIL, wrongCode));
        }
        assertThat(stored.get().getFailedAttempts()).isEqualTo(5);
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_INVALID,
                () -> serviceAt(START.plusSeconds(2)).verifyStudentEmail(USER_ID, EMAIL, code));
    }

    @Test
    @DisplayName("가입 시 미검증·다른 이메일·30분 지난 인증을 거부한다")
    void requiresMatchingEmailAndRecentVerificationForRegistration() {
        serviceAt(START).sendStudentEmailVerification(USER_ID, EMAIL);
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED,
                () -> serviceAt(START).consumeVerifiedStudentEmail(USER_ID, EMAIL));

        serviceAt(START.plusSeconds(1)).verifyStudentEmail(USER_ID, EMAIL, sentCode());
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED,
                () -> serviceAt(START.plusSeconds(2)).consumeVerifiedStudentEmail(USER_ID, "other@kw.ac.kr"));
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED,
                () -> serviceAt(START.plusSeconds(1801)).consumeVerifiedStudentEmail(USER_ID, EMAIL));
    }

    @Test
    @DisplayName("인증 후 재발송하면 기존 검증 상태를 초기화한다")
    void resendingAfterVerificationClearsApproval() {
        serviceAt(START).sendStudentEmailVerification(USER_ID, EMAIL);
        serviceAt(START.plusSeconds(1)).verifyStudentEmail(USER_ID, EMAIL, sentCode());

        serviceAt(START.plusSeconds(60)).sendStudentEmailVerification(USER_ID, EMAIL);

        assertThat(stored.get().getVerifiedAt()).isNull();
        assertError(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED,
                () -> serviceAt(START.plusSeconds(61)).consumeVerifiedStudentEmail(USER_ID, EMAIL));
    }

    @Test
    @DisplayName("메일 발송 실패 시 성공 처리하지 않는다")
    void doesNotReportSuccessWhenMailDeliveryFails() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(MimeMessage.class));

        assertError(ErrorCode.STUDENT_EMAIL_DELIVERY_FAILED,
                () -> serviceAt(START).sendStudentEmailVerification(USER_ID, EMAIL));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("학생 인증 메일은 인증번호가 들어간 HTML 본문과 텍스트 대체 본문을 함께 보낸다")
    void sendsHtmlVerificationMailWithPlainTextFallback() throws Exception {
        serviceAt(START).sendStudentEmailVerification(USER_ID, EMAIL);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("[골목인턴] 학생 이메일 인증번호");
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly(EMAIL);
        assertThat(message.getFrom()).extracting(Object::toString).containsExactly("sender@example.com");

        String code = sentCode();
        assertThat(lastSentPart("text/html"))
                .startsWith("<!DOCTYPE html>")
                .contains(code)
                .doesNotContain("{{code}}");
        assertThat(new BCryptPasswordEncoder().matches(code, stored.get().getCodeHash())).isTrue();
    }

    @Test
    @DisplayName("사업자등록정보 진위 확인을 외부 클라이언트에 위임하고 결과를 반환한다")
    void verifiesBusinessForPendingUser() {
        LocalDate openedAt = LocalDate.of(2020, 3, 1);
        when(businessVerificationClient.verify("1234567890", openedAt, "김사장")).thenReturn(true);

        VerifyOwnerBusinessCommand command = VerifyOwnerBusinessCommand.of(
                "KAKAO_12345", "김사장", openedAt, "1234567890");
        assertThat(serviceAt(START).verifyOwnerBusiness(command))
                .isTrue();
        verify(businessVerificationClient).verify("1234567890", openedAt, "김사장");
    }

    private AuthService serviceAt(Instant instant) {
        return new AuthService(verificationRepository,
                mailSender, businessVerificationClient,
                Clock.fixed(instant, ZoneOffset.UTC), "sender@example.com");
    }

    private String sentCode() {
        return lastSentPart("text/plain").replaceAll("\\D", "").substring(0, 6);
    }

    private String lastSentPart(String mimeType) {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        try {
            MimeMessage message = captor.getAllValues().getLast();
            message.saveChanges();
            return findPart(message, mimeType);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String findPart(Part part, String mimeType) throws Exception {
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                String found = findPart(child, mimeType);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        return part.isMimeType(mimeType) ? (String) part.getContent() : null;
    }

    private void assertError(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
