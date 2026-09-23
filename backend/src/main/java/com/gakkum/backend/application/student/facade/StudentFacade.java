package com.gakkum.backend.application.student.facade;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.student.dto.StudentRegistrationRequest;
import com.gakkum.backend.application.student.dto.StudentRegistrationRequest.CertificateRequest;
import com.gakkum.backend.application.student.dto.StudentRegistrationResponse;
import com.gakkum.backend.domain.certificate.dto.CertificateCommandDto.AddStudentCertificateCommand;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyCommandDto.AddStudentSpecialtyCommand;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StudentFacade {

    private final UserService userService;
    private final StudentService studentService;
    private final SpecialtyService specialtyService;
    private final CertificateService certificateService;
    private final JwtService jwtService;

    @Transactional
    public StudentRegistrationResponse register(String username, StudentRegistrationRequest request) {
        String normalizedEmail = request.getNormalizedEmail();
        User user = userService.validateStudentRegistration(username, normalizedEmail);
        studentService.validateStudentNumberAvailable(request.getStudentNumber());
        List<Long> specialtyIds = request.getNormalizedSpecialtyIds();
        specialtyService.validateSpecialtyIds(specialtyIds);

        userService.completeStudentRegistration(user, request.getStudentName(), normalizedEmail);
        Student student = studentService.createStudentProfile(request.toCommand(user.getId()));

        for (Long specialtyId : specialtyIds) {
            specialtyService.addStudentSpecialty(AddStudentSpecialtyCommand.of(student.getId(), specialtyId));
        }

        for (CertificateRequest certificate : request.getNormalizedCertificates()) {
            certificateService.addStudentCertificate(AddStudentCertificateCommand.of(
                    student.getId(),
                    certificate.getNormalizedCertificateName(),
                    certificate.getAcquiredYear(),
                    certificate.getNormalizedIssuingOrganization()
            ));
        }

        String accessToken = jwtService.issueAccessToken(username, UserRole.STUDENT);
        String refreshToken = jwtService.replaceRefreshToken(username, UserRole.STUDENT);
        return StudentRegistrationResponse.of(accessToken, refreshToken);
    }
}
