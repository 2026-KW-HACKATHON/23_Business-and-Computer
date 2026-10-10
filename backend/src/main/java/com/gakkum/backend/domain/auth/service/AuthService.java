package com.gakkum.backend.domain.auth.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder CODE_ENCODER = new BCryptPasswordEncoder();
    private static final String VERIFICATION_MAIL_TEMPLATE = "mail/student-email-verification.html";
    // 인증 메일 본문의 그림. 메일에 붙여 보내야 메일 프로그램이 외부 주소 없이 그림을 보여 준다 (Content-ID → 파일)
    private static final Map<String, String> VERIFICATION_MAIL_IMAGES = Map.of(
            "golmok-app-icon", "mail/golmok-app-icon.png",
            "golmok-logo", "mail/golmok-logo.png",
            "golmok-tagline", "mail/golmok-tagline.png");

    private final StudentEmailVerificationRepository verificationRepository;
    private final JavaMailSender mailSender;
    private final NtsBusinessVerificationClient businessVerificationClient;
    private final Clock clock;
    private final String senderAddress;
    private final String verificationMailTemplate;

    public AuthService(
            StudentEmailVerificationRepository verificationRepository,
            JavaMailSender mailSender,
            NtsBusinessVerificationClient businessVerificationClient,
            Clock clock,
            @Value("${spring.mail.username}") String senderAddress) {
        this.verificationRepository = verificationRepository;
        this.mailSender = mailSender;
        this.businessVerificationClient = businessVerificationClient;
        this.clock = clock;
        this.senderAddress = senderAddress;
        this.verificationMailTemplate = loadTemplate(VERIFICATION_MAIL_TEMPLATE);
    }

    /**
     * 사업자등록정보 진위 확인. 가입 대기 사용자 검증은 호출하는 퍼사드가 먼저 수행한다.
     * @param command
     * @return 국세청 확인 결과
     */
    public boolean verifyOwnerBusiness(VerifyOwnerBusinessCommand command) {
        return businessVerificationClient.verify(
                command.getBusinessNumber(), command.getOpenedAt(), command.getRepresentativeName());
    }

    /**
     * 학생 이메일 인증번호 발송. 가입 대기 여부와 이메일 중복은 호출하는 퍼사드가 먼저 검증한다.
     * @param userId 검증을 마친 가입 대기 사용자 ID
     * @param email
     */
    @Transactional
    public void sendStudentEmailVerification(String userId, String email) {
        Instant now = clock.instant();
        // 동시 재발송이 모두 대기 시간을 통과하지 않도록 기존 인증 행을 잠근 뒤 확인한다
        StudentEmailVerification verification = verificationRepository.findLockedByUserId(userId).orElse(null);
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
            // 잠글 행이 없는 첫 발송이 겹치면 기본 키 충돌로 하나만 남기고, 나머지는 재발송 대기로 거부한다
            try {
                verificationRepository.saveAndFlush(
                        StudentEmailVerification.create(userId, email, codeHash, now));
            } catch (DataIntegrityViolationException exception) {
                throw new BusinessException(ErrorCode.STUDENT_EMAIL_VERIFICATION_COOLDOWN);
            }
        } else {
            verification.renew(email, codeHash, now);
            verificationRepository.save(verification);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(senderAddress);
            helper.setTo(email);
            helper.setSubject("[골목인턴] 학생 이메일 인증번호");
            helper.setText(
                    "골목인턴 학생 인증번호는 " + code + "이에요. 10분 안에 가입 화면에 입력해 주세요.",
                    verificationMailTemplate.replace("{{code}}", code));
            for (Map.Entry<String, String> image : VERIFICATION_MAIL_IMAGES.entrySet()) {
                helper.addInline(image.getKey(), new ClassPathResource(image.getValue()), "image/png");
            }
            mailSender.send(message);
        } catch (MailException | MessagingException exception) {
            throw new BusinessException(ErrorCode.STUDENT_EMAIL_DELIVERY_FAILED);
        }
    }

    /**
     * 학생 이메일 인증번호 확인. 오입력 횟수는 예외를 던져도 저장되어야 하므로 BusinessException에 롤백하지 않는다.
     * 동시 확인 요청이 같은 오입력 횟수를 읽어 5회 제한을 넘지 않도록 인증 행을 잠근 뒤 확인한다.
     * @param userId 검증을 마친 가입 대기 사용자 ID
     * @param email
     * @param code
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void verifyStudentEmail(String userId, String email, String code) {
        StudentEmailVerification verification = verificationRepository.findLockedByUserId(userId)
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

    /**
     * 가입 시 검증된 학생 이메일을 한 번 소비한다. 사용자 검증은 호출하는 퍼사드가 먼저 수행한다.
     * @param userId 검증을 마친 가입 대기 사용자 ID
     * @param email
     */
    @Transactional
    public void consumeVerifiedStudentEmail(String userId, String email) {
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
}
