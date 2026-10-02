package com.gakkum.backend.domain.certificate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Year;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.domain.certificate.dto.CertificateCommandDto.AddStudentCertificateCommand;
import com.gakkum.backend.domain.certificate.entity.StudentCertificate;
import com.gakkum.backend.domain.certificate.repository.StudentCertificateRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class CertificateServiceTest {

    private final StudentCertificateRepository studentCertificateRepository =
            mock(StudentCertificateRepository.class);
    private final CertificateService certificateService = new CertificateService(studentCertificateRepository);

    @Test
    void addsStudentCertificate() {
        when(studentCertificateRepository.save(any(StudentCertificate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AddStudentCertificateCommand command = AddStudentCertificateCommand.of(
                10L,
                "정보처리기사",
                2025,
                "한국산업인력공단");

        StudentCertificate savedCertificate = certificateService.addStudentCertificate(command);

        ArgumentCaptor<StudentCertificate> certificateCaptor = ArgumentCaptor.forClass(StudentCertificate.class);
        verify(studentCertificateRepository).save(certificateCaptor.capture());
        assertThat(savedCertificate).isSameAs(certificateCaptor.getValue());
        assertThat(savedCertificate.getStudentProfileId()).isEqualTo(10L);
        assertThat(savedCertificate.getCertificateName()).isEqualTo("정보처리기사");
        assertThat(savedCertificate.getAcquiredYear()).isEqualTo(2025);
        assertThat(savedCertificate.getIssuingOrganization()).isEqualTo("한국산업인력공단");
    }

    @Test
    void rejectsFutureAcquiredYear() {
        AddStudentCertificateCommand command = AddStudentCertificateCommand.of(
                10L,
                "정보처리기사",
                Year.now().getValue() + 1,
                "한국산업인력공단");

        assertThatThrownBy(() -> certificateService.addStudentCertificate(command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }
}
