package com.gakkum.backend.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.gakkum.backend.domain.auth.entity.StudentEmailVerification;

import jakarta.persistence.LockModeType;

public interface StudentEmailVerificationRepository extends JpaRepository<StudentEmailVerification, String> {

    /** 재발송 대기 시간과 오입력 횟수 확인이 동시 요청에 우회되지 않도록 사용자의 인증 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudentEmailVerification> findLockedByUserId(String userId);
}
