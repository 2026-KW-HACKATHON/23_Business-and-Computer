package com.gakkum.backend.domain.user.repository;

import com.gakkum.backend.domain.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameAndIsLock(String username, Boolean isLock);
    boolean existsByEmailIgnoreCase(String email);
}
