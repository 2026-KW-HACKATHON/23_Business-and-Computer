package com.gakkum.backend.application.student;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalLikeRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.entity.Review;
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

@DisplayName("사장님용 학생 프로필 조회 전체 흐름 (GET /students/{studentProfileId}/profile)")
class StudentProfileFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB7S";
    private static final Long OWNER_PROFILE_ID = 5L;
    private static final Long OTHER_OWNER_PROFILE_ID = 6L;
    private static final Long STUDENT_PROFILE_ID = 7L;
    private static final String STUDENT_PROFILE_IMAGE_URL = "https://cdn.example.com/students/7/profile.png";
    private static final String DEMO_SESSION_ID = "01K58M6PJV8VAJMXHBHJ2PNBD1";
    private static final String OTHER_DEMO_SESSION_ID = "01K58M6PJV8VAJMXHBHJ2PNBD2";
    private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 9, 30, 23, 30);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final StudentSpecialtyRepository studentSpecialtyRepository = mock(StudentSpecialtyRepository.class);
    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final SpecialtyCategoryRepository specialtyCategoryRepository = mock(SpecialtyCategoryRepository.class);
    private final StudentCertificateRepository studentCertificateRepository =
            mock(StudentCertificateRepository.class);
    private final ProposalRepository proposalRepository = mock(ProposalRepository.class);
    private final ProposalSpecialtyRepository proposalSpecialtyRepository = mock(ProposalSpecialtyRepository.class);
    private final ProposalLikeRepository proposalLikeRepository = mock(ProposalLikeRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);

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
                new ProposalService(proposalRepository, proposalSpecialtyRepository, proposalLikeRepository),
                new JobService(jobRepository, jobSpecialtyRepository, jobApplicationRepository,
                        jobSubmissionRepository, Clock.systemUTC()),
                new ReviewService(reviewRepository),
                paymentService,
                new OwnerService(ownerRepository));

        mockMvc = MockMvcBuilders.standaloneSetup(new StudentController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("사장님이 학생 프로필을 조회하면 지원자 프로필과 같은 학생 정보(프로필 사진 포함)·통계·전체 특기·자격증·포트폴리오·패널티 횟수·리뷰를 응답한다")
    void returnsStudentProfile() throws Exception {
        givenOwner();
        givenStudent("https://example.com/portfolio", 3);
        givenActivity();

        getProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.student.studentProfileId").value(7))
                .andExpect(jsonPath("$.data.student.name").value("김가꿈"))
                .andExpect(jsonPath("$.data.student.profileImageUrl").value(STUDENT_PROFILE_IMAGE_URL))
                .andExpect(jsonPath("$.data.student.university").value("광운대학교"))
                .andExpect(jsonPath("$.data.student.major").value("소프트웨어학부"))
                // 학번 전체 대신 입학연도 두 자리만 내린다
                .andExpect(jsonPath("$.data.student.studentNumber").value("23"))
                .andExpect(jsonPath("$.data.proposalCount").value(12))
                .andExpect(jsonPath("$.data.completedJobCount").value(5))
                // 학생이 등록한 전체 특기를 대분류·소분류 ID 오름차순으로 내린다
                .andExpect(jsonPath("$.data.specialtyCategories[*].id").value(contains(1, 2)))
                .andExpect(jsonPath("$.data.specialtyCategories[0].name").value("개발"))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[*].id").value(contains(11, 12)))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[0].name").value("웹 개발"))
                .andExpect(jsonPath("$.data.specialtyCategories[1].name").value("디자인"))
                .andExpect(jsonPath("$.data.specialtyCategories[1].specialties[*].id").value(contains(21)))
                // Repository가 정렬해 돌려준 순서를 그대로 내린다
                .andExpect(jsonPath("$.data.certificates.length()").value(2))
                .andExpect(jsonPath("$.data.certificates[0].certificateName").value("정보처리기사"))
                .andExpect(jsonPath("$.data.certificates[0].acquiredYear").value(2025))
                .andExpect(jsonPath("$.data.certificates[1].certificateName").value("SQLD"))
                .andExpect(jsonPath("$.data.portfolioUrl").value("https://example.com/portfolio"))
                .andExpect(jsonPath("$.data.penaltyCount").value(3))
                .andExpect(jsonPath("$.data.reviewCount").value(4))
                .andExpect(jsonPath("$.data.reviews.length()").value(4))
                // 작성 시각 내림차순 → 같은 시각은 리뷰 ID 내림차순 → 작성 시각이 없으면 마지막
                .andExpect(jsonPath("$.data.reviews[*].content").value(contains(
                        "요청한 내용을 잘 반영해 주셨어요.", "같은 시각 뒤 리뷰", "같은 시각 앞 리뷰", "작성 시각 없는 리뷰")))
                .andExpect(jsonPath("$.data.reviews[0].storeName").value("월계카페"))
                .andExpect(jsonPath("$.data.reviews[0].jobTitle").value("카페 홈페이지 제작"))
                .andExpect(jsonPath("$.data.reviews[0].rating").value(5))
                .andExpect(jsonPath("$.data.reviews[0].createdAt").value("2026-09-30"))
                // 다른 사장님에게 받은 리뷰도 포함한다
                .andExpect(jsonPath("$.data.reviews[1].storeName").value("광운분식"))
                .andExpect(jsonPath("$.data.reviews[1].jobTitle").value("메뉴판 디자인"))
                .andExpect(jsonPath("$.data.reviews[3].createdAt").value(nullValue()))
                .andExpect(jsonPath("$.data.reviews[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.reviews[0].positivePoints").doesNotExist());

        // 통계는 지원자 프로필과 같이 취소된 제안을 포함한 전체 제안 수와 완료(CLOSED) 의뢰 수다
        verify(proposalRepository).countByStudentProfileId(STUDENT_PROFILE_ID);
        verify(jobRepository).countBySelectedStudentProfileIdAndStatus(STUDENT_PROFILE_ID, JobStatus.CLOSED);
        // 리뷰가 참조하는 의뢰 전부와 그 의뢰의 사장님 전부를 중복 없이 조회한다
        verify(jobRepository).findAllById(exactly(Set.of(201L, 202L, 203L, 204L)));
        verify(ownerRepository).findAllById(exactly(Set.of(OWNER_PROFILE_ID, OTHER_OWNER_PROFILE_ID)));
    }

    @Test
    @DisplayName("글 없는 리뷰도 리뷰 목록과 리뷰 수에 포함하고 내용을 null로 내린다")
    void returnsReviewWithoutContent() throws Exception {
        givenOwner();
        givenStudent("https://example.com/portfolio", 3);
        givenActivity();
        when(reviewRepository.findByStudentProfileId(STUDENT_PROFILE_ID)).thenReturn(List.of(
                review(302L, 202L, REVIEWED_AT.minusDays(1), "꼼꼼했어요.", 4),
                review(301L, 201L, REVIEWED_AT, null, 5)));

        getProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(2))
                .andExpect(jsonPath("$.data.reviews.length()").value(2))
                .andExpect(jsonPath("$.data.reviews[0].jobTitle").value("카페 홈페이지 제작"))
                .andExpect(jsonPath("$.data.reviews[0]", hasKey("content")))
                .andExpect(jsonPath("$.data.reviews[0].content").value(nullValue()))
                .andExpect(jsonPath("$.data.reviews[0].rating").value(5))
                .andExpect(jsonPath("$.data.reviews[1].content").value("꼼꼼했어요."));
    }

    @Test
    @DisplayName("프로필 사진·특기·자격증·제안·완료 의뢰·리뷰·포트폴리오·패널티가 없으면 빈 배열, 0, null로 응답한다")
    void returnsDefaultsForStudentWithoutData() throws Exception {
        givenOwner();
        givenStudent(null);

        getProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.studentProfileId").value(7))
                .andExpect(jsonPath("$.data.student.profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.proposalCount").value(0))
                .andExpect(jsonPath("$.data.completedJobCount").value(0))
                .andExpect(jsonPath("$.data.specialtyCategories").isArray())
                .andExpect(jsonPath("$.data.specialtyCategories").isEmpty())
                .andExpect(jsonPath("$.data.certificates").isArray())
                .andExpect(jsonPath("$.data.certificates").isEmpty())
                .andExpect(jsonPath("$.data.portfolioUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.penaltyCount").value(0))
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                .andExpect(jsonPath("$.data.reviews").isArray())
                .andExpect(jsonPath("$.data.reviews").isEmpty());

        verifyNoInteractions(specialtyRepository, specialtyCategoryRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 30 })
    @DisplayName("의뢰 관계가 있으면 제안 관계는 조회하지 않고, 리뷰 수가 늘어도 Repository는 종류별로 한 번씩만 호출한다")
    void readsEachRepositoryOnceRegardlessOfReviewCount(int reviewCount) throws Exception {
        givenOwner();
        givenStudent(null);
        List<Long> reviewedJobIds = LongStream.rangeClosed(1, reviewCount).map(id -> 1000 + id).boxed().toList();
        when(reviewRepository.findByStudentProfileId(STUDENT_PROFILE_ID)).thenReturn(reviewedJobIds.stream()
                .map(jobId -> review(jobId, jobId, REVIEWED_AT, "리뷰", 4))
                .toList());
        givenJobsFoundById(reviewedJobIds.stream()
                .map(jobId -> job(jobId, jobId % 2 == 0 ? OWNER_PROFILE_ID : OTHER_OWNER_PROFILE_ID,
                        JobStatus.CLOSED, STUDENT_PROFILE_ID, "의뢰"))
                .toList());
        givenOwnersFoundById(List.of(
                owner(OWNER_PROFILE_ID, "월계카페"), owner(OTHER_OWNER_PROFILE_ID, "광운분식")));
        when(studentSpecialtyRepository.findByStudentProfileIdIn(anyCollection()))
                .thenReturn(List.of(StudentSpecialty.create(STUDENT_PROFILE_ID, 11L)));
        givenSpecialties();

        getProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(reviewCount))
                .andExpect(jsonPath("$.data.reviews.length()").value(reviewCount));

        verify(userRepository).findByUsernameAndIsLock(USERNAME, false);
        verify(userRepository).findById(STUDENT_USER_ID);
        verify(studentRepository).findById(STUDENT_PROFILE_ID);
        // 열람 관계는 조회자의 사장님 프로필과 의뢰 관계 한 번으로 확인한다
        verify(ownerRepository).findByUserId(USER_ID);
        verify(jobRepository).existsOwnerJobRelatedToStudent(OWNER_PROFILE_ID, STUDENT_PROFILE_ID);
        verify(studentSpecialtyRepository).findByStudentProfileIdIn(List.of(STUDENT_PROFILE_ID));
        verify(specialtyRepository).findAllById(any());
        verify(specialtyCategoryRepository).findAllById(any());
        verify(reviewRepository).findByStudentProfileId(STUDENT_PROFILE_ID);
        verify(studentCertificateRepository)
                .findByStudentProfileIdOrderByAcquiredYearDescIdDesc(STUDENT_PROFILE_ID);
        verify(proposalRepository).countByStudentProfileId(STUDENT_PROFILE_ID);
        verify(jobRepository).countBySelectedStudentProfileIdAndStatus(STUDENT_PROFILE_ID, JobStatus.CLOSED);
        // 리뷰한 의뢰와 그 매장 말고는 의뢰·사장님 프로필을 읽지 않는다. 리뷰가 없으면 매장 조회도 없다
        verify(jobRepository).findAllById(exactly(Set.copyOf(reviewedJobIds)));
        if (reviewCount > 0) {
            // 리뷰한 의뢰가 하나면 홀수 ID라 다른 사장님의 의뢰뿐이다
            verify(ownerRepository).findAllById(exactly(reviewCount == 1
                    ? Set.of(OTHER_OWNER_PROFILE_ID)
                    : Set.of(OWNER_PROFILE_ID, OTHER_OWNER_PROFILE_ID)));
        }
        verifyNoMoreInteractions(userRepository, ownerRepository, jobRepository, studentRepository,
                studentSpecialtyRepository, specialtyRepository, specialtyCategoryRepository, reviewRepository,
                studentCertificateRepository, proposalRepository);
        // 지원서는 의뢰 관계 쿼리 안에서만 확인하고, 제출물·결제는 읽지 않는다
        verifyNoInteractions(jobApplicationRepository, jobSpecialtyRepository, jobSubmissionRepository,
                proposalSpecialtyRepository, proposalLikeRepository, paymentService);
    }

    @Test
    @DisplayName("학생이 이 사장님에게 제안을 보냈으면 의뢰 관계가 없어도 조회할 수 있다")
    void returnsStudentWhoProposedToOwner() throws Exception {
        givenOwner();
        givenStudent(null);
        givenNoJobRelation();
        when(proposalRepository.existsByOwnerProfileIdAndStudentProfileId(OWNER_PROFILE_ID, STUDENT_PROFILE_ID))
                .thenReturn(true);

        getProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.studentProfileId").value(7))
                .andExpect(jsonPath("$.data.student.studentNumber").value("23"));
    }

    @ParameterizedTest
    @CsvSource(value = { "NULL", DEMO_SESSION_ID }, nullValues = "NULL")
    @DisplayName("학생의 제안이 사장님 탐색 목록에 보이면 의뢰·받은 제안 관계가 없어도 조회할 수 있다 (탐색 제안 상세의 프로필 보기)")
    void returnsStudentWhoseProposalIsShownInExplore(String demoSessionId) throws Exception {
        givenOwner(demoSessionId);
        givenStudentFound(student(null), demoSessionId);
        givenNoJobRelation();
        when(proposalRepository.existsByStudentProfileIdAndDemoSessionIdAndStatusNotIn(
                STUDENT_PROFILE_ID, demoSessionId, List.of(ProposalStatus.CANCELLED, ProposalStatus.REJECTED)))
                .thenReturn(true);

        getProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.studentProfileId").value(7));

        verify(proposalRepository).existsByOwnerProfileIdAndStudentProfileId(OWNER_PROFILE_ID, STUDENT_PROFILE_ID);
    }

    @Test
    @DisplayName("의뢰 지원·선택, 받은 제안, 탐색 제안 어느 관계도 없는 학생은 없는 학생과 같은 STUDENT_PROFILE_404를 응답하고 활동 이력을 조회하지 않는다")
    void rejectsUnrelatedStudent() throws Exception {
        givenOwner();
        givenStudent("https://example.com/portfolio", 3);
        givenNoJobRelation();

        getProfile()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDENT_PROFILE_404"))
                .andExpect(jsonPath("$.error.message").value("존재하지 않는 학생입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(jobRepository).existsOwnerJobRelatedToStudent(OWNER_PROFILE_ID, STUDENT_PROFILE_ID);
        verify(proposalRepository).existsByOwnerProfileIdAndStudentProfileId(OWNER_PROFILE_ID, STUDENT_PROFILE_ID);
        verify(proposalRepository).existsByStudentProfileIdAndDemoSessionIdAndStatusNotIn(
                STUDENT_PROFILE_ID, null, List.of(ProposalStatus.CANCELLED, ProposalStatus.REJECTED));
        verifyNoMoreInteractions(jobRepository, proposalRepository);
        verifyNoInteractions(studentSpecialtyRepository, specialtyRepository, specialtyCategoryRepository,
                reviewRepository, studentCertificateRepository);
    }

    @Test
    @DisplayName("사장님 프로필이 없는 조회자는 STUDENT_PROFILE_404를 응답하고 관계·활동 이력을 조회하지 않는다")
    void rejectsOwnerWithoutOwnerProfile() throws Exception {
        givenOwner();
        givenStudent(null);
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        getProfile()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDENT_PROFILE_404"));

        verifyNoInteractions(jobRepository, proposalRepository, reviewRepository, studentCertificateRepository);
    }

    @Test
    @DisplayName("데모 사장님은 같은 격리 범위의 데모 학생을 조회할 수 있다")
    void returnsStudentInSameDemoSession() throws Exception {
        givenOwner(DEMO_SESSION_ID);
        givenStudentFound(student(null), DEMO_SESSION_ID);

        getProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.studentProfileId").value(7));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL," + DEMO_SESSION_ID,
            DEMO_SESSION_ID + ",NULL",
            DEMO_SESSION_ID + "," + OTHER_DEMO_SESSION_ID
    }, nullValues = "NULL")
    @DisplayName("격리 범위가 다른 학생은 없는 학생과 같은 STUDENT_PROFILE_404를 응답하고 활동 이력을 조회하지 않는다")
    void rejectsStudentInOtherDemoSession(String ownerDemoSessionId, String studentDemoSessionId) throws Exception {
        givenOwner(ownerDemoSessionId);
        givenStudentFound(student(null), studentDemoSessionId);

        getProfile()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDENT_PROFILE_404"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoActivityAccess();
    }

    @Test
    @DisplayName("없는 학생은 STUDENT_PROFILE_404를 응답하고 활동 이력을 조회하지 않는다")
    void rejectsMissingStudent() throws Exception {
        givenOwner();
        when(studentRepository.findById(STUDENT_PROFILE_ID)).thenReturn(Optional.empty());

        getProfile()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDENT_PROFILE_404"));

        verify(userRepository).findByUsernameAndIsLock(USERNAME, false);
        verifyNoMoreInteractions(userRepository);
        verifyNoActivityAccess();
    }

    @Test
    @DisplayName("잠겼거나 없는 사용자는 401을 응답하고 학생을 조회하지 않는다")
    void rejectsLockedUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        getProfile()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(studentRepository);
        verifyNoActivityAccess();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "OWNER", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("학생·가입 대기 등 사장님이 아닌 역할은 STUDENT_PROFILE_403_OWNER를 응답하고 학생 데이터를 조회하지 않는다")
    void rejectsNonOwnerRole(UserRole role) throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(user(role, null)));

        getProfile()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("STUDENT_PROFILE_403_OWNER"));

        verifyNoInteractions(studentRepository);
        verifyNoActivityAccess();
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-1", "abc", "1.5" })
    @DisplayName("양수가 아니거나 숫자가 아닌 ID는 400을 응답하고 사용자 조회를 시작하지 않는다")
    void rejectsInvalidId(String studentProfileId) throws Exception {
        mockMvc.perform(get("/students/" + studentProfileId + "/profile").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, studentRepository);
    }

    @Test
    @DisplayName("학생의 사용자 정보가 없으면 500을 응답한다")
    void failsWhenStudentUserIsMissing() throws Exception {
        givenOwner();
        givenStudent(null);
        when(userRepository.findById(STUDENT_USER_ID)).thenReturn(Optional.empty());

        getProfile()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));

        verifyNoActivityAccess();
    }

    @Test
    @DisplayName("학생이 등록한 특기 참조가 누락되면 500을 응답한다")
    void failsWhenStudentSpecialtyIsMissing() throws Exception {
        givenOwner();
        givenStudent(null);
        givenSpecialties();
        when(studentSpecialtyRepository.findByStudentProfileIdIn(anyCollection()))
                .thenReturn(List.of(StudentSpecialty.create(STUDENT_PROFILE_ID, 99L)));

        getProfile()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("리뷰가 참조하는 의뢰가 없으면 리뷰를 빼지 않고 500을 응답한다")
    void failsWhenReviewedJobIsMissing() throws Exception {
        givenOwner();
        givenStudent(null);
        givenActivity();
        givenJobsFoundById(List.of(
                job(201L, OWNER_PROFILE_ID, JobStatus.CLOSED, STUDENT_PROFILE_ID, "카페 홈페이지 제작")));

        getProfile()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("리뷰한 의뢰의 사장님 프로필이 없으면 500을 응답한다")
    void failsWhenReviewOwnerIsMissing() throws Exception {
        givenOwner();
        givenStudent(null);
        givenActivity();
        givenOwnersFoundById(List.of(owner(OWNER_PROFILE_ID, "월계카페")));

        getProfile()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    private ResultActions getProfile() throws Exception {
        return mockMvc.perform(get("/students/{studentProfileId}/profile", STUDENT_PROFILE_ID)
                .principal(authentication));
    }

    private void verifyNoActivityAccess() {
        verifyNoInteractions(studentSpecialtyRepository, specialtyRepository, specialtyCategoryRepository,
                reviewRepository, studentCertificateRepository, proposalRepository, jobRepository, ownerRepository);
    }

    // 조회자 사용자와 사장님 프로필을 준비하고, 기본으로 학생이 이 사장님의 의뢰에 지원한 관계가 있다고 둔다
    private void givenOwner() {
        givenOwner(null);
    }

    private void givenOwner(String demoSessionId) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false))
                .thenReturn(Optional.of(user(UserRole.OWNER, demoSessionId)));
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(owner(OWNER_PROFILE_ID, "월계카페")));
        when(jobRepository.existsOwnerJobRelatedToStudent(OWNER_PROFILE_ID, STUDENT_PROFILE_ID)).thenReturn(true);
    }

    private void givenNoJobRelation() {
        when(jobRepository.existsOwnerJobRelatedToStudent(OWNER_PROFILE_ID, STUDENT_PROFILE_ID)).thenReturn(false);
    }

    // 패널티 횟수를 지정하지 않으면 학생 생성 시 기본값을 그대로 쓴다
    private void givenStudent(String portfolioUrl) {
        givenStudentFound(student(portfolioUrl), null);
    }

    private void givenStudent(String portfolioUrl, int penaltyCount) {
        givenStudentFound(Student.builder()
                .id(STUDENT_PROFILE_ID)
                .userId(STUDENT_USER_ID)
                .university("광운대학교")
                .studentNumber("2023000007")
                .major("소프트웨어학부")
                .portfolioUrl(portfolioUrl)
                .penaltyCount(penaltyCount)
                .profileImageUrl(STUDENT_PROFILE_IMAGE_URL)
                .build(), null);
    }

    private static Student student(String portfolioUrl) {
        return Student.builder()
                .id(STUDENT_PROFILE_ID)
                .userId(STUDENT_USER_ID)
                .university("광운대학교")
                .studentNumber("2023000007")
                .major("소프트웨어학부")
                .portfolioUrl(portfolioUrl)
                .build();
    }

    private void givenStudentFound(Student student, String demoSessionId) {
        when(studentRepository.findById(STUDENT_PROFILE_ID)).thenReturn(Optional.of(student));
        when(userRepository.findById(STUDENT_USER_ID)).thenReturn(Optional.of(User.builder()
                .id(STUDENT_USER_ID)
                .name("김가꿈")
                .role(UserRole.STUDENT)
                .isLock(false)
                .demoSessionId(demoSessionId)
                .build()));
    }

    /**
     * 제안 12건, 완료 의뢰 5건, 특기 21·12·11, 자격증 2개
     * 리뷰 301(의뢰 201, 본인 매장): 9/30 작성
     * 리뷰 303·302(의뢰 203·202, 다른 매장): 9/29 같은 시각 작성
     * 리뷰 304(의뢰 204, 다른 매장): 작성 시각 없음
     */
    private void givenActivity() {
        when(proposalRepository.countByStudentProfileId(STUDENT_PROFILE_ID)).thenReturn(12L);
        when(jobRepository.countBySelectedStudentProfileIdAndStatus(STUDENT_PROFILE_ID, JobStatus.CLOSED))
                .thenReturn(5L);
        when(studentSpecialtyRepository.findByStudentProfileIdIn(anyCollection())).thenReturn(List.of(
                StudentSpecialty.create(STUDENT_PROFILE_ID, 21L),
                StudentSpecialty.create(STUDENT_PROFILE_ID, 12L),
                StudentSpecialty.create(STUDENT_PROFILE_ID, 11L)));
        givenSpecialties();
        when(studentCertificateRepository.findByStudentProfileIdOrderByAcquiredYearDescIdDesc(STUDENT_PROFILE_ID))
                .thenReturn(List.of(
                        StudentCertificate.create(STUDENT_PROFILE_ID, "정보처리기사", 2025),
                        StudentCertificate.create(STUDENT_PROFILE_ID, "SQLD", 2024)));
        when(reviewRepository.findByStudentProfileId(STUDENT_PROFILE_ID)).thenReturn(List.of(
                review(304L, 204L, null, "작성 시각 없는 리뷰", 3),
                review(302L, 202L, REVIEWED_AT.minusDays(1), "같은 시각 앞 리뷰", 4),
                review(301L, 201L, REVIEWED_AT, "요청한 내용을 잘 반영해 주셨어요.", 5),
                review(303L, 203L, REVIEWED_AT.minusDays(1), "같은 시각 뒤 리뷰", 4)));
        givenJobsFoundById(List.of(
                job(201L, OWNER_PROFILE_ID, JobStatus.CLOSED, STUDENT_PROFILE_ID, "카페 홈페이지 제작"),
                job(202L, OTHER_OWNER_PROFILE_ID, JobStatus.CLOSED, STUDENT_PROFILE_ID, "전단지 디자인"),
                job(203L, OTHER_OWNER_PROFILE_ID, JobStatus.CLOSED, STUDENT_PROFILE_ID, "메뉴판 디자인"),
                job(204L, OTHER_OWNER_PROFILE_ID, JobStatus.CLOSED, STUDENT_PROFILE_ID, "SNS 홍보")));
        givenOwnersFoundById(List.of(
                owner(OWNER_PROFILE_ID, "월계카페"), owner(OTHER_OWNER_PROFILE_ID, "광운분식")));
    }

    /** 실제 Repository처럼 요청한 ID에 해당하는 의뢰만 돌려준다. */
    private void givenJobsFoundById(List<Job> jobs) {
        // 이미 스텁된 조회를 다시 스텁해도 기존 응답이 실행되지 않도록 doAnswer를 쓴다
        doAnswer(invocation -> {
            Set<Object> ids = requestedIds(invocation.getArgument(0));
            return jobs.stream().filter(job -> ids.contains(job.getId())).toList();
        }).when(jobRepository).findAllById(any());
    }

    /** 실제 Repository처럼 요청한 ID에 해당하는 사장님 프로필만 돌려준다. */
    private void givenOwnersFoundById(List<Owner> owners) {
        doAnswer(invocation -> {
            Set<Object> ids = requestedIds(invocation.getArgument(0));
            return owners.stream().filter(owner -> ids.contains(owner.getId())).toList();
        }).when(ownerRepository).findAllById(any());
    }

    private static Set<Object> requestedIds(Iterable<?> ids) {
        Set<Object> requested = new HashSet<>();
        ids.forEach(requested::add);
        return requested;
    }

    /** 일괄 조회에 넘긴 ID가 중복 없이 기대한 집합과 같은지 확인한다. */
    private static <T> Iterable<T> exactly(Set<T> expected) {
        return argThat(ids -> {
            List<Object> actual = new ArrayList<>();
            ids.forEach(actual::add);
            // 크기가 같고 집합도 같아야 중복([5, 5])과 누락을 모두 거부한다
            return actual.size() == expected.size() && new HashSet<>(actual).equals(expected);
        });
    }

    private void givenSpecialties() {
        when(specialtyRepository.findAllById(any())).thenAnswer(invocation -> {
            List<Specialty> found = new ArrayList<>();
            for (Object id : (Iterable<?>) invocation.getArgument(0)) {
                if (id.equals(11L)) {
                    found.add(specialty(11L, 1L, "웹 개발"));
                } else if (id.equals(12L)) {
                    found.add(specialty(12L, 1L, "앱 개발"));
                } else if (id.equals(21L)) {
                    found.add(specialty(21L, 2L, "포스터 디자인"));
                }
            }
            return found;
        });
        when(specialtyCategoryRepository.findAllById(any())).thenReturn(List.of(
                SpecialtyCategory.builder().id(2L).name("디자인").build(),
                SpecialtyCategory.builder().id(1L).name("개발").build()));
    }

    private static User user(UserRole role, String demoSessionId) {
        return User.builder()
                .id(USER_ID)
                .username(USERNAME)
                .role(role)
                .isLock(false)
                .demoSessionId(demoSessionId)
                .build();
    }

    private static Owner owner(Long id, String storeName) {
        return Owner.builder().id(id).storeName(storeName).build();
    }

    private static Job job(Long id, Long ownerProfileId, JobStatus status, Long selectedStudentProfileId,
            String title) {
        return Job.builder()
                .id(id)
                .ownerProfileId(ownerProfileId)
                .title(title)
                .status(status)
                .selectedStudentProfileId(selectedStudentProfileId)
                .build();
    }

    private static Review review(Long id, Long jobId, LocalDateTime createdAt, String content, Integer rating) {
        return Review.builder()
                .id(id)
                .jobId(jobId)
                .ownerProfileId(OWNER_PROFILE_ID)
                .studentProfileId(STUDENT_PROFILE_ID)
                .positivePoints(List.of())
                .content(content)
                .rating(rating)
                .createdAt(createdAt)
                .build();
    }

    private static Specialty specialty(Long id, Long categoryId, String name) {
        return Specialty.builder().id(id).specialtyCategoryId(categoryId).name(name).build();
    }
}
