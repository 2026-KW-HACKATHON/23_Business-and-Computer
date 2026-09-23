package com.gakkum.backend.application.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.student.controller.StudentController;
import com.gakkum.backend.application.student.facade.StudentFacade;
import com.gakkum.backend.domain.certificate.entity.StudentCertificate;
import com.gakkum.backend.domain.certificate.repository.StudentCertificateRepository;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.jwt.entity.RefreshToken;
import com.gakkum.backend.domain.jwt.repository.RefreshRepository;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.specialty.entity.StudentSpecialty;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.repository.StudentSpecialtyRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;
import com.gakkum.backend.util.JWTUtil;

class StudentRegistrationFlowTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final StudentSpecialtyRepository studentSpecialtyRepository = mock(StudentSpecialtyRepository.class);
    private final StudentCertificateRepository studentCertificateRepository =
            mock(StudentCertificateRepository.class);
    private final RefreshRepository refreshRepository = mock(RefreshRepository.class);
    private final JWTUtil jwtUtil = mock(JWTUtil.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(refreshRepository, jwtUtil);
        UserService userService = new UserService(userRepository, jwtService);
        StudentService studentService = new StudentService(studentRepository);
        SpecialtyService specialtyService = new SpecialtyService(specialtyRepository, studentSpecialtyRepository);
        CertificateService certificateService = new CertificateService(studentCertificateRepository);
        StudentFacade facade = new StudentFacade(
                userService,
                studentService,
                specialtyService,
                certificateService,
                jwtService);
        StudentController controller = new StudentController(facade);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registersStudentThroughControllerFacadeAndDomainServices() throws Exception {
        User pendingUser = User.builder()
                .id("01K58M6PJV8VAJMXHBHJ2PNB5C")
                .username("KAKAO_12345")
                .isLock(false)
                .role(UserRole.PENDING)
                .build();
        when(userRepository.findByUsernameAndIsLock("KAKAO_12345", false))
                .thenReturn(java.util.Optional.of(pendingUser));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            return Student.builder()
                    .id(10L)
                    .userId(student.getUserId())
                    .university(student.getUniversity())
                    .studentNumber(student.getStudentNumber())
                    .major(student.getMajor())
                    .portfolioUrl(student.getPortfolioUrl())
                    .introduction(student.getIntroduction())
                    .profileImageUrl(student.getProfileImageUrl())
                    .build();
        });
        when(specialtyRepository.countByIdIn(List.of(1L, 2L))).thenReturn(2L);
        when(specialtyRepository.existsById(anyLong())).thenReturn(true);
        when(jwtUtil.createJWT("KAKAO_12345", "ROLE_STUDENT", true)).thenReturn("student-access-token");
        when(jwtUtil.createJWT("KAKAO_12345", "ROLE_STUDENT", false)).thenReturn("student-refresh-token");
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "KAKAO_12345",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_PENDING")));

        mockMvc.perform(post("/auth/student")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "김광운",
                          "email": "KWANGWOON@KW.AC.KR",
                          "university": "광운대학교",
                          "studentNumber": "2024402001",
                          "major": "컴퓨터정보공학부",
                          "portfolioUrl": "https://portfolio.example.com",
                          "introduction": "나의 한 줄 소개",
                          "profileImageUrl": "https://image.example.com/profile.png",
                          "specialtyIds": [1, 2],
                          "certificates": [
                            {
                              "certificateName": "정보처리기사",
                              "acquiredYear": 2025,
                              "issuingOrganization": "한국산업인력공단"
                            }
                          ]
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("student-access-token"))
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.allOf(
                    Matchers.containsString("refreshToken=student-refresh-token"),
                    Matchers.containsString("HttpOnly"),
                    Matchers.containsString("SameSite=Lax"))));

        assertThat(pendingUser.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(pendingUser.getEmail()).isEqualTo("kwangwoon@kw.ac.kr");
        assertThat(pendingUser.getName()).isEqualTo("김광운");

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(studentCaptor.capture());
        assertThat(studentCaptor.getValue().getUserId()).isEqualTo(pendingUser.getId());
        assertThat(studentCaptor.getValue().getStudentNumber()).isEqualTo("2024402001");

        ArgumentCaptor<StudentSpecialty> specialtyCaptor = ArgumentCaptor.forClass(StudentSpecialty.class);
        verify(studentSpecialtyRepository, times(2)).save(specialtyCaptor.capture());
        assertThat(specialtyCaptor.getAllValues())
                .extracting(StudentSpecialty::getStudentProfileId)
                .containsOnly(10L);

        ArgumentCaptor<StudentCertificate> certificateCaptor =
                ArgumentCaptor.forClass(StudentCertificate.class);
        verify(studentCertificateRepository).save(certificateCaptor.capture());
        assertThat(certificateCaptor.getValue().getStudentProfileId()).isEqualTo(10L);

        verify(refreshRepository).deleteByUsername("KAKAO_12345");
        verify(refreshRepository).flush();
        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshRepository).save(refreshTokenCaptor.capture());
        assertThat(refreshTokenCaptor.getValue().getRefresh()).isEqualTo("student-refresh-token");
    }
}
