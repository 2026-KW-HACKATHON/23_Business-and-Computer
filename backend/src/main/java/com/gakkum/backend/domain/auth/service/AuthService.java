package com.gakkum.backend.domain.auth.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import com.gakkum.backend.domain.auth.client.NtsBusinessVerificationClient;
import com.gakkum.backend.domain.auth.dto.AuthCommandDto.VerifyOwnerBusinessCommand;
import com.gakkum.backend.domain.auth.entity.StudentEmailVerification;
import com.gakkum.backend.domain.auth.repository.StudentEmailVerificationRepository;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder CODE_ENCODER = new BCryptPasswordEncoder();
    private static final String VERIFICATION_MAIL_TEMPLATE = "mail/student-email-verification.html";

    private final UserRepository userRepository;
    private final StudentEmailVerificationRepository verificationRepository;
    private final JavaMailSender mailSender;
    private final NtsBusinessVerificationClient businessVerificationClient;
    private final Clock clock;
    private final String senderAddress;
    private final String verificationMailTemplate;

    public AuthService(
            UserRepository userRepository,
            StudentEmailVerificationRepository verificationRepository,
            JavaMailSender mailSender,
            NtsBusinessVerificationClient businessVerificationClient,
            Clock clock,
            @Value("${spring.mail.username}") String senderAddress) {
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
        this.mailSender = mailSender;
        this.businessVerificationClient = businessVerificationClient;
        this.clock = clock;
        this.senderAddress = senderAddress;
        this.verificationMailTemplate = loadTemplate(VERIFICATION_MAIL_TEMPLATE);
    }

    public boolean verifyOwnerBusiness(VerifyOwnerBusinessCommand command) {
        User user = userRepository.findByUsernameAndIsLock(command.getUsername(), false)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
        return businessVerificationClient.verify(
                command.getBusinessNumber(), command.getOpenedAt(), command.getRepresentativeName());
    }

    @Transactional
    public void sendStudentEmailVerification(String username, String email) {
        User user = pendingUser(username);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        Instant now = clock.instant();
        StudentEmailVerification verification = verificationRepository.findById(user.getId()).orElse(null);
        if (verification != null && verification.getSentAt().plusSeconds(60).isAfter(now)) {
            throw new BusinessException(ErrorCode.STUDENT_EMAIL_VERIFICATION_COOLDOWN);
        }

        String code;
        do {
            code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        } while (verification != null && verification.getCodeHash() != null
                && CODE_ENCODER.matches(code, verification.getCodeHash()));
        String codeHash = CODE_ENCODER.encode(code);
        if (verification == null) {
            verification = StudentEmailVerification.create(user.getId(), email, codeHash, now);
        } else {
            verification.renew(email, codeHash, now);
        }
        verificationRepository.save(verification);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(senderAddress);
            helper.setTo(email);
            helper.setSubject("[가꿈] 학생 이메일 인증번호");
            helper.setText(
                    "학생 이메일 인증번호는 " + code + "입니다. 10분 안에 입력해 주세요.",
                    verificationMailTemplate.replace("{{code}}", code));
            mailSender.send(message);
        } catch (MailException | MessagingException exception) {
            throw new BusinessException(ErrorCode.STUDENT_EMAIL_DELIVERY_FAILED);
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void verifyStudentEmail(String username, String email, String code) {
        User user = pendingUser(username);
        StudentEmailVerification verification = verificationRepository.findById(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_EMAIL_VERIFICATION_INVALID));
        Instant now = clock.instant();
        if (!verification.getEmail().equals(email)
                || verification.getCodeHash() == null
                || verification.getCodeExpiresAt() == null
                || !now.isBefore(verification.getCodeExpiresAt())
                || verification.getFailedAttempts() >= 5) {
            throw new BusinessException(ErrorCode.STUDENT_EMAIL_VERIFICATION_INVALID);
        }

        if (!CODE_ENCODER.matches(code, verification.getCodeHash())) {
            verification.recordFailure();
            throw new BusinessException(ErrorCode.STUDENT_EMAIL_VERIFICATION_INVALID);
        }
        verification.markVerified(now);
    }

    @Transactional
    public void consumeVerifiedStudentEmail(String userId, String email) {
        userRepository.findByIdAndIsLockFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        StudentEmailVerification verification = verificationRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED));
        Instant verifiedAt = verification.getVerifiedAt();
        if (!verification.getEmail().equals(email)
                || verifiedAt == null
                || !clock.instant().isBefore(verifiedAt.plusSeconds(1800))) {
            throw new BusinessException(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED);
        }
        verificationRepository.delete(verification);
    }

    private static String loadTemplate(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("메일 템플릿을 읽을 수 없습니다: " + path, exception);
        }
    }

    private User pendingUser(String username) {
        User user = userRepository.findByUsernameAndIsLockFalse(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
        return user;
    }
}
