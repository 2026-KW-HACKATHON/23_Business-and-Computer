package com.gakkum.backend.domain.user.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "users")
public class User {

    @Id
    @Column(name = "user_id", length = 26)
    private String id;

    @Setter
    @Column(unique = true, length = 255)
    private String email;

    @Setter
    @Column(length = 255)
    private String name;

    @Column(name = "username", unique = true, nullable = false, updatable = false)
    private String username;

    @Column(name = "is_lock", nullable = false)
    private Boolean isLock;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider_type")
    private SocialProviderType socialProviderType;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    // 데모 로그인이 만든 데이터의 격리 범위. 실제 데이터는 null
    @Column(name = "demo_session_id", length = 26, updatable = false)
    private String demoSessionId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void completeStudentRegistration(String name, String email) {
        this.name = name;
        this.email = email;
        this.role = UserRole.STUDENT;
    }

    public void completeOwnerRegistration(String name) {
        this.name = name;
        this.role = UserRole.OWNER;
    }
}
