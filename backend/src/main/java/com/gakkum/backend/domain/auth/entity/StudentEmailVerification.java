package com.gakkum.backend.domain.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "student_email_verifications")
public class StudentEmailVerification {

    @Id
    @Column(name = "user_id", length = 26)
    private String userId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "code_hash", length = 255)
    private String codeHash;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "code_expires_at")
    private Instant codeExpiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    public static StudentEmailVerification create(String userId, String email, String codeHash, Instant now) {
        StudentEmailVerification verification = new StudentEmailVerification();
        verification.userId = userId;
        verification.renew(email, codeHash, now);
        return verification;
    }

    public void renew(String email, String codeHash, Instant now) {
        this.email = email;
        this.codeHash = codeHash;
        this.sentAt = now;
        this.codeExpiresAt = now.plusSeconds(600);
        this.verifiedAt = null;
        this.failedAttempts = 0;
    }

    public void recordFailure() {
        failedAttempts++;
    }

    public void markVerified(Instant now) {
        codeHash = null;
        codeExpiresAt = null;
        verifiedAt = now;
    }
}
