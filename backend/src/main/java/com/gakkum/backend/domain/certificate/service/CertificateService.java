package com.gakkum.backend.domain.certificate.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.certificate.dto.CertificateCommandDto.AddStudentCertificateCommand;
import com.gakkum.backend.domain.certificate.entity.StudentCertificate;
import com.gakkum.backend.domain.certificate.repository.StudentCertificateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private final StudentCertificateRepository studentCertificateRepository;

    @Transactional
    public StudentCertificate addStudentCertificate(AddStudentCertificateCommand command) {
        StudentCertificate studentCertificate = StudentCertificate.create(
                command.getStudentProfileId(),
                command.getCertificateName(),
                command.getAcquiredYear(),
                command.getIssuingOrganization());

        return studentCertificateRepository.save(studentCertificate);
    }
}
