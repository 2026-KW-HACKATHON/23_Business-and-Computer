package com.gakkum.backend.domain.certificate.service;

import java.time.Year;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.certificate.dto.CertificateCommandDto.AddStudentCertificateCommand;
import com.gakkum.backend.domain.certificate.entity.StudentCertificate;
import com.gakkum.backend.domain.certificate.repository.StudentCertificateRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private final StudentCertificateRepository studentCertificateRepository;

    @Transactional
    public StudentCertificate addStudentCertificate(AddStudentCertificateCommand command) {
        if (command.getAcquiredYear() > Year.now().getValue()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        StudentCertificate studentCertificate = StudentCertificate.create(
                command.getStudentProfileId(),
                command.getCertificateName(),
                command.getAcquiredYear(),
                command.getIssuingOrganization());

        return studentCertificateRepository.save(studentCertificate);
    }
}
