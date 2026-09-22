package com.gakkum.backend.domain.certificate.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.certificate.entity.StudentCertificate;

public interface StudentCertificateRepository extends JpaRepository<StudentCertificate, Long> {
}
