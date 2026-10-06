package com.gakkum.backend.application.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.student.dto.StudentMeUpdateRequest;
import com.gakkum.backend.application.student.dto.StudentMeUpdateRequest.CertificateRequest;
import com.gakkum.backend.application.student.facade.StudentFacade;
import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentCertificateResult;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentMeResult;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentSpecialtyResult;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;

/**
 * 특기의 (학생, 특기) 고유 제약과 삭제 후 재저장 순서, 저장 도중 실패의 롤백은 PostgreSQL에서만 확인할 수 있어 실제 DB로 검증한다.
 * 커밋과 롤백을 실제로 확인해야 하므로 클래스 트랜잭션을 끄고 직접 데이터를 정리하며, 공용 DB를 건드리지 않도록 로컬 PostgreSQL에서만 실행한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ StudentFacade.class, StudentService.class, SpecialtyService.class, SpecialtyCategoryService.class,
        CertificateService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("학생 내 정보 수정 PostgreSQL 통합 (목록 교체·반복 저장·다른 학생 보존·롤백)")
class StudentMeUpdatePersistenceIntegrationTest {

    private static final String USERNAME = "TEST_ME_UPDATE_STUDENT";
    private static final String OTHER_USERNAME = "TEST_ME_UPDATE_OTHER";

    @Autowired
    private StudentFacade studentFacade;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private SpecialtyCategoryRepository specialtyCategoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private CertificateService certificateService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ProposalService proposalService;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private OwnerService ownerService;

    private final List<Long> studentProfileIds = new ArrayList<>();
    private final List<Long> specialtyIds = new ArrayList<>();
    private Long categoryId;
    private Long studentProfileId;
    private Long otherStudentProfileId;
    private Long poster;
    private Long logo;
    private Long shortForm;

    @BeforeEach
    void setUp() {
        categoryId = specialtyCategoryRepository.saveAndFlush(
                SpecialtyCategory.builder().name("내 정보 수정 테스트 " + UUID.randomUUID()).build()).getId();
        poster = specialty("포스터");
        logo = specialty("로고");
        shortForm = specialty("숏폼");
        studentProfileId = student(USERNAME);
        otherStudentProfileId = student(OTHER_USERNAME);
    }

    @AfterEach
    void cleanUp() {
        reset(certificateService);
        for (Long id : studentProfileIds) {
            jdbcTemplate.update("delete from student_specialties where student_profile_id = ?", id);
            jdbcTemplate.update("delete from student_certificates where student_profile_id = ?", id);
            jdbcTemplate.update("delete from student_profiles where id = ?", id);
        }
        for (Long id : specialtyIds) {
            jdbcTemplate.update("delete from specialties where id = ?", id);
        }
        jdbcTemplate.update("delete from specialty_categories where id = ?", categoryId);
    }

    @Test
    @DisplayName("PostgreSQL에서 특기 일부를 유지한 채 목록을 바꿔도 고유 제약에 걸리지 않고 조회에 다섯 항목이 반영되며 수정 불가 항목은 그대로다")
    void replacesListsKeepingSomeSpecialties() {
        update(USERNAME, "https://example.com/first.png", "첫 소개", "https://example.com/first",
                List.of(poster, logo), List.of(CertificateRequest.of("GTQ 1급", 2023)));

        update(USERNAME, "https://example.com/profile.png", "  디자인을 좋아하는 학생입니다.  ",
                "https://example.com/portfolio", List.of(logo, shortForm),
                List.of(CertificateRequest.of("정보처리기사", 2025), CertificateRequest.of("컴퓨터활용능력 1급", 2024)));

        assertThat(storedSpecialtyIds(studentProfileId)).containsExactlyInAnyOrder(logo, shortForm);
        StudentMeResult me = studentFacade.getMe(USERNAME);
        assertThat(me.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
        assertThat(me.getIntroduction()).isEqualTo("디자인을 좋아하는 학생입니다.");
        assertThat(me.getPortfolioUrl()).isEqualTo("https://example.com/portfolio");
        assertThat(me.getSpecialtyCategories()).hasSize(1);
        assertThat(me.getSpecialtyCategories().get(0).getSpecialties())
                .extracting(StudentSpecialtyResult::getId)
                .containsExactly(logo, shortForm);
        assertThat(me.getCertificates()).extracting(StudentCertificateResult::getCertificateName)
                .containsExactly("정보처리기사", "컴퓨터활용능력 1급");
        assertThat(me.getCertificates()).extracting(StudentCertificateResult::getAcquiredYear)
                .containsExactly(2025, 2024);

        Student stored = studentRepository.findById(studentProfileId).orElseThrow();
        assertThat(stored.getUniversity()).isEqualTo("광운대학교");
        assertThat(stored.getMajor()).isEqualTo("소프트웨어학부");
        assertThat(stored.getStudentNumber()).hasSize(10);
        assertThat(stored.getPenaltyCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("PostgreSQL에서 같은 내용을 반복 저장해도 특기와 자격증 행이 늘지 않는다")
    void savesSameContentRepeatedly() {
        for (int attempt = 0; attempt < 3; attempt++) {
            update(USERNAME, "https://example.com/profile.png", "소개", "https://example.com/portfolio",
                    List.of(poster, logo),
                    List.of(CertificateRequest.of("정보처리기사", 2025), CertificateRequest.of("정보처리기사", 2025)));
        }

        assertThat(storedSpecialtyIds(studentProfileId)).containsExactlyInAnyOrder(poster, logo);
        // 자격증 중복은 제한하지 않아 보낸 그대로 두 건이다
        assertThat(storedCertificates(studentProfileId)).containsExactly("정보처리기사:2025", "정보처리기사:2025");
    }

    @Test
    @DisplayName("PostgreSQL에서 빈 목록과 빈 문자열로 저장하면 본인의 값만 지워지고 다른 학생의 프로필·특기·자격증은 그대로다")
    void clearsOnlyOwnData() {
        update(USERNAME, "https://example.com/profile.png", "소개", "https://example.com/portfolio",
                List.of(poster, logo), List.of(CertificateRequest.of("정보처리기사", 2025)));
        update(OTHER_USERNAME, "https://example.com/other.png", "다른 학생 소개", "https://example.com/other",
                List.of(poster, shortForm), List.of(CertificateRequest.of("GTQ 1급", 2024)));

        update(USERNAME, "", " ", null, List.of(), List.of());

        Student stored = studentRepository.findById(studentProfileId).orElseThrow();
        assertThat(stored.getProfileImageUrl()).isNull();
        assertThat(stored.getIntroduction()).isNull();
        assertThat(stored.getPortfolioUrl()).isNull();
        assertThat(storedSpecialtyIds(studentProfileId)).isEmpty();
        assertThat(storedCertificates(studentProfileId)).isEmpty();

        Student other = studentRepository.findById(otherStudentProfileId).orElseThrow();
        assertThat(other.getProfileImageUrl()).isEqualTo("https://example.com/other.png");
        assertThat(other.getIntroduction()).isEqualTo("다른 학생 소개");
        assertThat(other.getPortfolioUrl()).isEqualTo("https://example.com/other");
        assertThat(storedSpecialtyIds(otherStudentProfileId)).containsExactlyInAnyOrder(poster, shortForm);
        assertThat(storedCertificates(otherStudentProfileId)).containsExactly("GTQ 1급:2024");
    }

    @Test
    @DisplayName("PostgreSQL에서 자격증 저장 도중 실패하면 프로필 문자열·특기·자격증 변경이 모두 롤백된다")
    void rollsBackEverythingWhenSavingFailsMidway() {
        update(USERNAME, "https://example.com/profile.png", "소개", "https://example.com/portfolio",
                List.of(poster, logo), List.of(CertificateRequest.of("정보처리기사", 2025)));
        // 첫 자격증은 저장되고 둘째 자격증에서 실패한다
        doCallRealMethod().doThrow(new IllegalStateException("저장 실패"))
                .when(certificateService).addStudentCertificate(any());

        assertThatThrownBy(() -> update(USERNAME, "https://example.com/new.png", "새 소개",
                "https://example.com/new", List.of(logo, shortForm),
                List.of(CertificateRequest.of("GTQ 1급", 2024), CertificateRequest.of("컴퓨터활용능력 1급", 2023))))
                .isInstanceOf(IllegalStateException.class);

        Student stored = studentRepository.findById(studentProfileId).orElseThrow();
        assertThat(stored.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
        assertThat(stored.getIntroduction()).isEqualTo("소개");
        assertThat(stored.getPortfolioUrl()).isEqualTo("https://example.com/portfolio");
        assertThat(storedSpecialtyIds(studentProfileId)).containsExactlyInAnyOrder(poster, logo);
        assertThat(storedCertificates(studentProfileId)).containsExactly("정보처리기사:2025");
    }

    private void update(String username, String profileImageUrl, String introduction, String portfolioUrl,
            List<Long> specialtyIds, List<CertificateRequest> certificates) {
        studentFacade.updateMe(StudentMeUpdateRequest.of(
                profileImageUrl, introduction, specialtyIds, certificates, portfolioUrl).toCommand(username));
    }

    private List<Long> storedSpecialtyIds(Long profileId) {
        return jdbcTemplate.queryForList(
                "select specialty_id from student_specialties where student_profile_id = ?", Long.class, profileId);
    }

    private List<String> storedCertificates(Long profileId) {
        return jdbcTemplate.queryForList("""
                select certificate_name || ':' || acquired_year from student_certificates
                where student_profile_id = ? order by id
                """, String.class, profileId);
    }

    private Long specialty(String name) {
        Long id = specialtyRepository.saveAndFlush(
                Specialty.builder().specialtyCategoryId(categoryId).name(name).build()).getId();
        specialtyIds.add(id);
        return id;
    }

    // 학생 프로필이 있는 활성 학생으로 로그인한 상태를 만든다
    private Long student(String username) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        String userId = unique.substring(0, 26);
        Long id = studentRepository.saveAndFlush(Student.builder()
                .userId(userId)
                .university("광운대학교")
                .studentNumber(unique.substring(0, 10))
                .major("소프트웨어학부")
                .penaltyCount(2)
                .build()).getId();
        studentProfileIds.add(id);
        when(userService.getActiveUser(username)).thenReturn(
                User.builder().id(userId).username(username).role(UserRole.STUDENT).name("김광운").build());
        return id;
    }
}
