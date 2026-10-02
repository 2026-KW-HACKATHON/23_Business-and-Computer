package com.gakkum.backend.domain.jwt.repository;

import com.gakkum.backend.domain.jwt.entity.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshRepository extends JpaRepository<RefreshToken, Long> {
    Boolean existsByRefresh(String refreshToken);
    void deleteByRefresh(String refresh);
    void deleteByUsername(String username);
}
