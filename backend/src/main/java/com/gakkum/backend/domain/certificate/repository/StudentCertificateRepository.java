package com.gakkum.backend.domain.certificate.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.certificate.entity.StudentCertificate;

public interface StudentCertificateRepository extends JpaRepository<StudentCertificate, Long> {
    List<StudentCertificate> findByStudentProfileIdOrderByAcquiredYearDescIdDesc(Long studentProfileId);

    void deleteByStudentProfileId(Long studentProfileId);
}
