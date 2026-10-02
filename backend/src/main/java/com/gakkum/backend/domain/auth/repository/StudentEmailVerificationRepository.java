package com.gakkum.backend.domain.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.auth.entity.StudentEmailVerification;

public interface StudentEmailVerificationRepository extends JpaRepository<StudentEmailVerification, String> {
}
