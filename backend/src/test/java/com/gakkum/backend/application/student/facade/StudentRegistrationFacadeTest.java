package com.gakkum.backend.application.student.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.gakkum.backend.application.student.dto.StudentRegistrationRequest;
import com.gakkum.backend.application.student.dto.StudentRegistrationRequest.CertificateRequest;
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

class StudentRegistrationFacadeTest {

    private final UserService userService = mock(UserService.class);
    private final StudentService studentService = mock(StudentService.class);
    private final SpecialtyService specialtyService = mock(SpecialtyService.class);
    private final CertificateService certificateService = mock(CertificateService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final StudentRegistrationFacade facade = new StudentRegistrationFacade(
            userService,
            studentService,
            specialtyService,
            certificateService,
            jwtService);

    @Test
    void coordinatesStudentRegistrationWithGeneratedProfileId() {
        User user = User.builder()
                .id("01K58M6PJV8VAJMXHBHJ2PNB5C")
                .username("KAKAO_12345")
                .isLock(false)
                .role(UserRole.PENDING)
                .build();
        Student student = Student.builder().id(10L).userId(user.getId()).build();
        when(userService.validateStudentRegistration("KAKAO_12345", "kwangwoon@kw.ac.kr"))
                .thenReturn(user);
        when(userService.completeStudentRegistration(user, "김광운", "kwangwoon@kw.ac.kr"))
                .thenReturn(user);
        when(studentService.createStudentProfile(any(CreateStudentProfileCommand.class))).thenReturn(student);
        when(jwtService.issueAccessToken("KAKAO_12345", UserRole.STUDENT)).thenReturn("access-token");
        when(jwtService.replaceRefreshToken("KAKAO_12345", UserRole.STUDENT)).thenReturn("refresh-token");
        StudentRegistrationRequest request = StudentRegistrationRequest.of(
                "김광운",
                "kwangwoon@kw.ac.kr",
                "광운대학교",
                "2024402001",
                "컴퓨터정보공학부",
                null,
                null,
                null,
                List.of(1L, 2L),
                List.of(CertificateRequest.of(
                        "정보처리기사", 2025, "한국산업인력공단")));

        StudentRegistrationResponse response = facade.register("KAKAO_12345", request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");

        InOrder order = inOrder(userService, studentService, specialtyService, certificateService, jwtService);
        order.verify(userService).validateStudentRegistration("KAKAO_12345", "kwangwoon@kw.ac.kr");
        order.verify(studentService).validateStudentNumberAvailable("2024402001");
        order.verify(specialtyService).validateSpecialtyIds(List.of(1L, 2L));
        order.verify(userService).completeStudentRegistration(user, "김광운", "kwangwoon@kw.ac.kr");
        order.verify(studentService).createStudentProfile(any(CreateStudentProfileCommand.class));

        ArgumentCaptor<AddStudentSpecialtyCommand> specialtyCaptor =
                ArgumentCaptor.forClass(AddStudentSpecialtyCommand.class);
        order.verify(specialtyService, org.mockito.Mockito.times(2))
                .addStudentSpecialty(specialtyCaptor.capture());
        assertThat(specialtyCaptor.getAllValues())
                .extracting(AddStudentSpecialtyCommand::getStudentProfileId)
                .containsOnly(10L);

        ArgumentCaptor<AddStudentCertificateCommand> certificateCaptor =
                ArgumentCaptor.forClass(AddStudentCertificateCommand.class);
        order.verify(certificateService).addStudentCertificate(certificateCaptor.capture());
        assertThat(certificateCaptor.getValue().getStudentProfileId()).isEqualTo(10L);

        order.verify(jwtService).issueAccessToken("KAKAO_12345", UserRole.STUDENT);
        order.verify(jwtService).replaceRefreshToken("KAKAO_12345", UserRole.STUDENT);
    }
}
