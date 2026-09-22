package com.gakkum.backend.application.student.facade;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.student.dto.StudentRegistrationCommand;
import com.gakkum.backend.application.student.dto.StudentRegistrationResponse;
import com.gakkum.backend.domain.certificate.dto.CertificateCommandDto.AddStudentCertificateCommand;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyCommandDto.AddStudentSpecialtyCommand;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.dto.StudentCommandDto.CreateStudentProfileCommand;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StudentRegistrationFacade {

    private final UserService userService;
    private final StudentService studentService;
    private final SpecialtyService specialtyService;
    private final CertificateService certificateService;
    private final JwtService jwtService;

    @Transactional
    public StudentRegistrationResponse register(String username, StudentRegistrationCommand command) {
        User user = userService.validateStudentRegistration(username, command.email());
        studentService.validateStudentNumberAvailable(command.studentNumber());
        specialtyService.validateSpecialtyIds(command.specialtyIds());

        userService.completeStudentRegistration(user, command.name(), command.email());
        Student student = studentService.createStudentProfile(CreateStudentProfileCommand.of(
                user.getId(),
                command.university(),
                command.studentNumber(),
                command.major(),
                command.portfolioUrl(),
                command.introduction(),
                command.profileImageUrl())
        );

        for (Long specialtyId : command.specialtyIds()) {
            specialtyService.addStudentSpecialty(AddStudentSpecialtyCommand.of(student.getId(), specialtyId));
        }

        for (StudentRegistrationCommand.CertificateCommand certificate : command.certificates()) {
            certificateService.addStudentCertificate(AddStudentCertificateCommand.of(
                    student.getId(),
                    certificate.certificateName(),
                    certificate.acquiredYear(),
                    certificate.issuingOrganization()
            ));
        }

        String accessToken = jwtService.issueAccessToken(username, UserRole.STUDENT);
        String refreshToken = jwtService.replaceRefreshToken(username, UserRole.STUDENT);
        return new StudentRegistrationResponse(accessToken, refreshToken);
    }
}
