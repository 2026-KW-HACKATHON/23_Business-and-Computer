package com.gakkum.backend.domain.user.repository;

import com.gakkum.backend.domain.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameAndIsLock(String username, Boolean isLock);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByUsernameAndIsLockFalse(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByIdAndIsLockFalse(String id);
    boolean existsByEmailIgnoreCase(String email);
}
