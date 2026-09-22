package com.gakkum.backend.domain.certificate.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.certificate.entity.StudentCertificate;
import com.gakkum.backend.domain.certificate.repository.StudentCertificateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private final StudentCertificateRepository studentCertificateRepository;

    @Transactional
    public StudentCertificate addStudentCertificate(
            Long studentProfileId,
            String certificateName,
            Integer acquiredYear,
            String issuingOrganization) {
        StudentCertificate studentCertificate = StudentCertificate.create(
                studentProfileId,
                certificateName,
                acquiredYear,
                issuingOrganization);

        return studentCertificateRepository.save(studentCertificate);
    }
}
