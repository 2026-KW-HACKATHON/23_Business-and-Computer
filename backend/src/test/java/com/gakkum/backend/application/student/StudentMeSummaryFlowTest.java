package com.gakkum.backend.application.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.media.service.MediaService;
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
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
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

import tools.jackson.databind.ObjectMapper;

@DisplayName("학생 내 정보 요약 조회 전체 흐름 (GET /students/me/summary)")
class StudentMeSummaryFlowTest {

    private static final String USERNAME = "KAKAO_67890";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final Long STUDENT_PROFILE_ID = 7L;
    private static final String URL = "/students/me/summary";
    private static final List<String> SUMMARY_FIELDS = List.of(
            "studentProfileId", "profileImageUrl", "name", "university", "studentNumber", "major", "introduction",
            "portfolioUrl", "completedJobCount", "specialtyCategories", "certificates");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final SpecialtyCategoryRepository specialtyCategoryRepository = mock(SpecialtyCategoryRepository.class);
    private final StudentSpecialtyRepository studentSpecialtyRepository = mock(StudentSpecialtyRepository.class);
    private final StudentCertificateRepository studentCertificateRepository =
            mock(StudentCertificateRepository.class);
    private final ProposalRepository proposalRepository = mock(ProposalRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

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
                new SpecialtyCategoryService(specialtyCategoryRepository, specialtyRepository),
                new ProposalService(proposalRepository, mock(ProposalSpecialtyRepository.class),
                        mock(ProposalLikeRepository.class)),
                new JobService(jobRepository, jobSpecialtyRepository, jobApplicationRepository,
                        mock(JobSubmissionRepository.class), Clock.systemUTC()),
                new ReviewService(reviewRepository),
                new PaymentService(paymentRepository, Clock.systemUTC()),
                new OwnerService(ownerRepository),
                mock(MediaService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new StudentController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(specialtyRepository.findAllById(anyIterable())).thenReturn(List.of(
                Specialty.builder().id(11L).specialtyCategoryId(1L).name("포스터").build(),
                Specialty.builder().id(12L).specialtyCategoryId(1L).name("로고").build(),
                Specialty.builder().id(21L).specialtyCategoryId(2L).name("숏폼").build()));
        when(specialtyCategoryRepository.findAllById(anyIterable())).thenReturn(List.of(
                SpecialtyCategory.builder().id(1L).name("디자인").build(),
                SpecialtyCategory.builder().id(2L).name("영상").build()));
    }

    @Test
    @DisplayName("학생 본인의 프로필·학과·완료 수·특기·자격증 11개 필드만 반환하고 리뷰·정산·제안은 조회하지 않는다")
    void returnsSummaryWithoutActivityQueries() throws Exception {
        givenStudentWithActivity();

        perform(URL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(11))
                .andExpect(jsonPath("$.data.studentProfileId").value(7))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.gakkum.test/profile.png"))
                .andExpect(jsonPath("$.data.name").value("김광운"))
                .andExpect(jsonPath("$.data.university").value("광운대학교"))
                .andExpect(jsonPath("$.data.studentNumber").value("24"))
                .andExpect(jsonPath("$.data.major").value("컴퓨터정보공학부"))
                .andExpect(jsonPath("$.data.introduction").value("포스터를 잘 만듭니다."))
                .andExpect(jsonPath("$.data.portfolioUrl").value("https://portfolio.gakkum.test/kim"))
                .andExpect(jsonPath("$.data.completedJobCount").value(4))
                // 대분류 ID·소분류 ID 오름차순
                .andExpect(jsonPath("$.data.specialtyCategories.length()").value(2))
                .andExpect(jsonPath("$.data.specialtyCategories[0].id").value(1))
                .andExpect(jsonPath("$.data.specialtyCategories[0].name").value("디자인"))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[0].id").value(11))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[1].id").value(12))
                .andExpect(jsonPath("$.data.specialtyCategories[1].specialties[0].name").value("숏폼"))
                .andExpect(jsonPath("$.data.certificates.length()").value(2))
                .andExpect(jsonPath("$.data.certificates[0].length()").value(2))
                .andExpect(jsonPath("$.data.certificates[0].certificateName").value("정보처리기사"))
                .andExpect(jsonPath("$.data.certificates[0].acquiredYear").value(2025));

        verifyNoInteractions(reviewRepository, paymentRepository, proposalRepository, ownerRepository,
                jobApplicationRepository, jobSpecialtyRepository);
    }

    @Test
    @DisplayName("요약의 11개 필드는 같은 학생의 GET /students/me 응답과 값이 같다")
    void matchesFullMeResponse() throws Exception {
        givenStudentWithActivity();

        Map<String, Object> summary = data(URL);
        Map<String, Object> full = data("/students/me");

        assertThat(summary).containsOnlyKeys(SUMMARY_FIELDS);
        SUMMARY_FIELDS.forEach(field -> assertThat(summary.get(field)).as(field).isEqualTo(full.get(field)));
    }

    @Test
    @DisplayName("특기·자격증·완료 의뢰가 없는 학생은 빈 목록과 0을 받고 사진 URL·소개·포트폴리오 URL은 저장된 null 그대로 받는다")
    void returnsEmptyDefaults() throws Exception {
        givenStudent(UserRole.STUDENT, null, null);

        perform(URL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(11))
                .andExpect(jsonPath("$.data.profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.introduction").value(nullValue()))
                .andExpect(jsonPath("$.data.portfolioUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.completedJobCount").value(0))
                .andExpect(jsonPath("$.data.specialtyCategories.length()").value(0))
                .andExpect(jsonPath("$.data.certificates.length()").value(0));
    }

    @Test
    @DisplayName("사장님이 조회하면 403 STUDENT_403_ME를 반환하고 학생 데이터를 조회하지 않는다")
    void rejectsNonStudent() throws Exception {
        givenStudent(UserRole.OWNER, null, null);

        perform(URL)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("STUDENT_403_ME"));
        verifyNoInteractions(studentRepository, studentSpecialtyRepository, studentCertificateRepository,
                jobRepository);
    }

    @Test
    @DisplayName("잠겼거나 존재하지 않는 사용자는 401 COMMON_401로 거부한다")
    void rejectsInactiveUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        perform(URL)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(studentRepository);
    }

    @Test
    @DisplayName("학생 역할인데 학생 프로필이 없으면 500 COMMON_500을 반환한다")
    void rejectsStudentWithoutProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).name("김광운").build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.empty());

        perform(URL)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
        verifyNoInteractions(studentSpecialtyRepository, studentCertificateRepository, jobRepository);
    }

    private ResultActions perform(String url) throws Exception {
        return mockMvc.perform(get(url).principal(authentication));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(String url) throws Exception {
        String body = perform(url).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return (Map<String, Object>) new ObjectMapper().readValue(body, Map.class).get("data");
    }

    private void givenStudentWithActivity() {
        givenStudent(UserRole.STUDENT, "https://cdn.gakkum.test/profile.png", "포스터를 잘 만듭니다.");
        when(jobRepository.countBySelectedStudentProfileIdAndStatus(STUDENT_PROFILE_ID, JobStatus.CLOSED))
                .thenReturn(4L);
        when(studentSpecialtyRepository.findByStudentProfileIdIn(List.of(STUDENT_PROFILE_ID))).thenReturn(List.of(
                StudentSpecialty.create(STUDENT_PROFILE_ID, 21L),
                StudentSpecialty.create(STUDENT_PROFILE_ID, 12L),
                StudentSpecialty.create(STUDENT_PROFILE_ID, 11L)));
        when(studentCertificateRepository.findByStudentProfileIdOrderByAcquiredYearDescIdDesc(STUDENT_PROFILE_ID))
                .thenReturn(List.of(
                        StudentCertificate.create(STUDENT_PROFILE_ID, "정보처리기사", 2025),
                        StudentCertificate.create(STUDENT_PROFILE_ID, "GTQ 1급", 2024)));
    }

    private void givenStudent(UserRole role, String profileImageUrl, String introduction) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(role).name("김광운").build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(Student.builder()
                .id(STUDENT_PROFILE_ID)
                .userId(STUDENT_USER_ID)
                .university("광운대학교")
                .studentNumber("2024402001")
                .major("컴퓨터정보공학부")
                .profileImageUrl(profileImageUrl)
                .introduction(introduction)
                // 소개가 있는 학생만 포트폴리오도 등록한 상태로 둔다
                .portfolioUrl(introduction == null ? null : "https://portfolio.gakkum.test/kim")
                .penaltyCount(1)
                .build()));
    }
}
