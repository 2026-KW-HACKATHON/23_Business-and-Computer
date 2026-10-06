package com.gakkum.backend.application.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Year;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.student.controller.StudentController;
import com.gakkum.backend.application.student.facade.StudentFacade;
import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.domain.certificate.entity.StudentCertificate;
import com.gakkum.backend.domain.certificate.repository.StudentCertificateRepository;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.repository.ProposalLikeRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.repository.ReviewRepository;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.entity.StudentSpecialty;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.repository.StudentSpecialtyRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("학생 내 정보 수정 전체 흐름 (PUT /students/me)")
class StudentMeUpdateFlowTest {

    private static final String USERNAME = "KAKAO_67890";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final Long STUDENT_PROFILE_ID = 7L;
    private static final String URL = "/students/me";
    private static final String OLD_IMAGE_URL = "https://cdn.gakkum.test/old.png";
    private static final String OLD_INTRODUCTION = "예전 소개";
    private static final String OLD_PORTFOLIO_URL = "https://old.gakkum.test/portfolio";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final StudentSpecialtyRepository studentSpecialtyRepository = mock(StudentSpecialtyRepository.class);
    private final StudentCertificateRepository studentCertificateRepository =
            mock(StudentCertificateRepository.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;
    private Student student;

    @BeforeEach
    void setUp() {
        JwtService jwtService = mock(JwtService.class);
        StudentFacade facade = new StudentFacade(
                new UserService(userRepository, jwtService),
                new StudentService(studentRepository),
                new SpecialtyService(specialtyRepository, studentSpecialtyRepository),
                new CertificateService(studentCertificateRepository),
                jwtService,
                mock(AuthService.class),
                new SpecialtyCategoryService(mock(SpecialtyCategoryRepository.class), specialtyRepository),
                new ProposalService(mock(ProposalRepository.class), mock(ProposalSpecialtyRepository.class),
                        mock(ProposalLikeRepository.class)),
                new JobService(mock(JobRepository.class), mock(JobSpecialtyRepository.class),
                        mock(JobApplicationRepository.class), mock(JobSubmissionRepository.class),
                        Clock.systemUTC()),
                new ReviewService(mock(ReviewRepository.class)),
                new PaymentService(mock(PaymentRepository.class), Clock.systemUTC()),
                new OwnerService(mock(OwnerRepository.class)));
        mockMvc = MockMvcBuilders.standaloneSetup(new StudentController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // 특기 기준 데이터는 1·2·3번만 있다
        when(specialtyRepository.countByIdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().filter(id -> id <= 3).count();
        });
        when(specialtyRepository.existsById(anyLong())).thenAnswer(
                invocation -> invocation.<Long>getArgument(0) <= 3);
    }

    @Test
    @DisplayName("학생 본인의 사진·소개·포트폴리오를 바꾸고 특기와 자격증은 본인 프로필의 기존 행을 지워 반영한 뒤 새로 저장하며 성공 여부만 반환한다")
    void replacesOwnEditableInformation() throws Exception {
        givenUser(UserRole.STUDENT);
        givenProfile();

        perform("""
                {
                  "profileImageUrl": "https://example.com/profile.png",
                  "introduction": "  디자인을 좋아하는 학생입니다.  ",
                  "specialtyIds": [1, 2],
                  "certificates": [
                    {"certificateName": " 정보처리기사 ", "acquiredYear": 2025},
                    {"certificateName": "GTQ 1급", "acquiredYear": 1900}
                  ],
                  "portfolioUrl": "https://example.com/portfolio"
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.success").value(true));

        assertThat(student.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
        assertThat(student.getIntroduction()).isEqualTo("디자인을 좋아하는 학생입니다.");
        assertThat(student.getPortfolioUrl()).isEqualTo("https://example.com/portfolio");
        verify(studentRepository).save(student);
        assertUneditableFieldsKept();

        InOrder specialtyOrder = inOrder(studentSpecialtyRepository);
        specialtyOrder.verify(studentSpecialtyRepository).deleteByStudentProfileId(STUDENT_PROFILE_ID);
        specialtyOrder.verify(studentSpecialtyRepository).flush();
        ArgumentCaptor<StudentSpecialty> specialties = ArgumentCaptor.forClass(StudentSpecialty.class);
        specialtyOrder.verify(studentSpecialtyRepository, times(2)).save(specialties.capture());
        assertThat(specialties.getAllValues()).extracting(StudentSpecialty::getStudentProfileId)
                .containsOnly(STUDENT_PROFILE_ID);
        assertThat(specialties.getAllValues()).extracting(StudentSpecialty::getSpecialtyId).containsExactly(1L, 2L);

        InOrder certificateOrder = inOrder(studentCertificateRepository);
        certificateOrder.verify(studentCertificateRepository).deleteByStudentProfileId(STUDENT_PROFILE_ID);
        certificateOrder.verify(studentCertificateRepository).flush();
        ArgumentCaptor<StudentCertificate> certificates = ArgumentCaptor.forClass(StudentCertificate.class);
        certificateOrder.verify(studentCertificateRepository, times(2)).save(certificates.capture());
        assertThat(certificates.getAllValues()).extracting(StudentCertificate::getStudentProfileId)
                .containsOnly(STUDENT_PROFILE_ID);
        assertThat(certificates.getAllValues()).extracting(StudentCertificate::getCertificateName)
                .containsExactly("정보처리기사", "GTQ 1급");
        assertThat(certificates.getAllValues()).extracting(StudentCertificate::getAcquiredYear)
                .containsExactly(2025, 1900);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"specialtyIds\": [], \"certificates\": []}",
            "{\"profileImageUrl\": null, \"introduction\": null, \"portfolioUrl\": null,"
                    + " \"specialtyIds\": [], \"certificates\": []}",
            "{\"profileImageUrl\": \"\", \"introduction\": \"\", \"portfolioUrl\": \"\","
                    + " \"specialtyIds\": [], \"certificates\": []}",
            "{\"introduction\": \"  \\n \", \"specialtyIds\": [], \"certificates\": []}" })
    @DisplayName("문자열 항목을 생략하거나 null·빈 문자열·공백만 보내면 저장된 값을 지우고 빈 목록은 기존 특기와 자격증을 모두 지운다")
    void clearsStringsAndLists(String body) throws Exception {
        givenUser(UserRole.STUDENT);
        givenProfile();

        perform(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(student.getProfileImageUrl()).isNull();
        assertThat(student.getIntroduction()).isNull();
        assertThat(student.getPortfolioUrl()).isNull();
        assertUneditableFieldsKept();
        verify(studentSpecialtyRepository).deleteByStudentProfileId(STUDENT_PROFILE_ID);
        verify(studentSpecialtyRepository, never()).save(any());
        verify(studentCertificateRepository).deleteByStudentProfileId(STUDENT_PROFILE_ID);
        verify(studentCertificateRepository, never()).save(any());
    }

    @Test
    @DisplayName("취득연도가 올해인 자격증은 저장한다")
    void acceptsCurrentYearCertificate() throws Exception {
        givenUser(UserRole.STUDENT);
        givenProfile();

        perform(body("[]", "[{\"certificateName\": \"정보처리기사\", \"acquiredYear\": " + Year.now().getValue() + "}]"))
                .andExpect(status().isOk());

        verify(studentCertificateRepository).save(any(StudentCertificate.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("잘못된 입력은 400으로 거부하고 아무것도 저장하지 않는다")
    void rejectsInvalidInput(String description, String body, String errorCode) throws Exception {
        givenUser(UserRole.STUDENT);
        givenProfile();

        perform(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(errorCode));

        assertNothingWritten();
    }

    static Stream<Arguments> invalidBodies() {
        String longUrl = "https://example.com/" + "a".repeat(236);
        String nextYear = String.valueOf(Year.now().getValue() + 1);
        return Stream.of(
                invalid("특기 목록 누락", "{\"certificates\": []}"),
                invalid("특기 목록 null", "{\"specialtyIds\": null, \"certificates\": []}"),
                invalid("자격증 목록 누락", "{\"specialtyIds\": []}"),
                invalid("자격증 목록 null", "{\"specialtyIds\": [], \"certificates\": null}"),
                invalid("HTTP(S)가 아닌 사진 URL", withStrings("\"profileImageUrl\": \"ftp://example.com/a.png\"")),
                invalid("공백이 있는 사진 URL", withStrings("\"profileImageUrl\": \"https://example.com/a b.png\"")),
                invalid("255자를 넘는 사진 URL", withStrings("\"profileImageUrl\": \"" + longUrl + "\"")),
                invalid("HTTP(S)가 아닌 포트폴리오 URL", withStrings("\"portfolioUrl\": \"example.com/portfolio\"")),
                invalid("공백만 있는 포트폴리오 URL", withStrings("\"portfolioUrl\": \" \"")),
                invalid("255자를 넘는 포트폴리오 URL", withStrings("\"portfolioUrl\": \"" + longUrl + "\"")),
                invalid("양수가 아닌 특기 ID", body("[1, 0]", "[]")),
                invalid("null 특기 ID", body("[1, null]", "[]")),
                invalid("숫자가 아닌 특기 ID", body("[\"디자인\"]", "[]")),
                Arguments.of("중복된 특기 ID", body("[1, 2, 1]", "[]"), "SPECIALTY_400_DUPLICATE"),
                Arguments.of("존재하지 않는 특기 ID", body("[1, 99]", "[]"), "SPECIALTY_400"),
                invalid("null 자격증 항목", body("[]", "[null]")),
                invalid("자격증 이름 누락", body("[]", "[{\"acquiredYear\": 2025}]")),
                invalid("공백만 있는 자격증 이름", body("[]", "[{\"certificateName\": \"  \", \"acquiredYear\": 2025}]")),
                invalid("255자를 넘는 자격증 이름",
                        body("[]", "[{\"certificateName\": \"" + "가".repeat(256) + "\", \"acquiredYear\": 2025}]")),
                invalid("취득연도 누락", body("[]", "[{\"certificateName\": \"정보처리기사\"}]")),
                invalid("1900년보다 이른 취득연도",
                        body("[]", "[{\"certificateName\": \"정보처리기사\", \"acquiredYear\": 1899}]")),
                invalid("올해보다 뒤인 취득연도",
                        body("[1]", "[{\"certificateName\": \"GTQ 1급\", \"acquiredYear\": 2025},"
                                + " {\"certificateName\": \"정보처리기사\", \"acquiredYear\": " + nextYear + "}]")),
                invalid("본문 없음", ""));
    }

    @Test
    @DisplayName("사장님이 수정하면 403 STUDENT_403_ME_UPDATE를 반환하고 학생 데이터를 건드리지 않는다")
    void rejectsNonStudent() throws Exception {
        givenUser(UserRole.OWNER);

        perform(body("[1]", "[]"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("STUDENT_403_ME_UPDATE"));

        verifyNoInteractions(studentRepository, specialtyRepository, studentSpecialtyRepository,
                studentCertificateRepository);
    }

    @Test
    @DisplayName("잠겼거나 존재하지 않는 사용자는 401 COMMON_401로 거부한다")
    void rejectsInactiveUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        perform(body("[1]", "[]"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(studentRepository, studentSpecialtyRepository, studentCertificateRepository);
    }

    @Test
    @DisplayName("학생 역할인데 학생 프로필이 없으면 500 COMMON_500을 반환하고 아무것도 저장하지 않는다")
    void rejectsStudentWithoutProfile() throws Exception {
        givenUser(UserRole.STUDENT);
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.empty());

        perform(body("[1]", "[]"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));

        verify(studentRepository, never()).save(any());
        verifyNoInteractions(studentSpecialtyRepository, studentCertificateRepository);
    }

    private ResultActions perform(String body) throws Exception {
        return mockMvc.perform(put(URL)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void givenUser(UserRole role) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(role).name("김광운").build()));
    }

    private void givenProfile() {
        student = Student.builder()
                .id(STUDENT_PROFILE_ID)
                .userId(STUDENT_USER_ID)
                .university("광운대학교")
                .studentNumber("2024402001")
                .major("컴퓨터정보공학부")
                .profileImageUrl(OLD_IMAGE_URL)
                .introduction(OLD_INTRODUCTION)
                .portfolioUrl(OLD_PORTFOLIO_URL)
                .penaltyCount(1)
                .build();
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(student));
    }

    private void assertUneditableFieldsKept() {
        assertThat(student.getUserId()).isEqualTo(STUDENT_USER_ID);
        assertThat(student.getUniversity()).isEqualTo("광운대학교");
        assertThat(student.getStudentNumber()).isEqualTo("2024402001");
        assertThat(student.getMajor()).isEqualTo("컴퓨터정보공학부");
        assertThat(student.getPenaltyCount()).isEqualTo(1);
        verify(userRepository, never()).save(any());
    }

    private void assertNothingWritten() {
        assertThat(student.getProfileImageUrl()).isEqualTo(OLD_IMAGE_URL);
        assertThat(student.getIntroduction()).isEqualTo(OLD_INTRODUCTION);
        assertThat(student.getPortfolioUrl()).isEqualTo(OLD_PORTFOLIO_URL);
        verify(studentRepository, never()).save(any());
        verifyNoInteractions(studentSpecialtyRepository, studentCertificateRepository);
    }

    private static Arguments invalid(String description, String body) {
        return Arguments.of(description, body, "COMMON_400");
    }

    private static String body(String specialtyIds, String certificates) {
        return "{\"specialtyIds\": " + specialtyIds + ", \"certificates\": " + certificates + "}";
    }

    private static String withStrings(String stringField) {
        return "{" + stringField + ", \"specialtyIds\": [], \"certificates\": []}";
    }
}
