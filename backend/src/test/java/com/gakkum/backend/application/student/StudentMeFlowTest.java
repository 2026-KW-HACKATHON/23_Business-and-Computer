package com.gakkum.backend.application.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
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
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
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
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
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

@DisplayName("학생 내 정보 조회 전체 흐름 (GET /students/me)")
class StudentMeFlowTest {

    private static final String USERNAME = "KAKAO_67890";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final Long STUDENT_PROFILE_ID = 7L;
    private static final Long OTHER_STUDENT_PROFILE_ID = 8L;
    private static final String URL = "/students/me";

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

    private final List<Job> jobs = new ArrayList<>();
    private final List<Review> reviews = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();
    private final List<JobApplication> applications = new ArrayList<>();

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
                new OwnerService(ownerRepository));
        mockMvc = MockMvcBuilders.standaloneSetup(new StudentController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(reviewRepository.findByStudentProfileId(STUDENT_PROFILE_ID)).thenReturn(reviews);
        when(reviewRepository.findAverageRatingByStudentProfileId(STUDENT_PROFILE_ID)).thenReturn(null);
        when(paymentRepository.findLatestCompletedByStudentProfileId(
                eq(STUDENT_PROFILE_ID), eq(JobStatus.CLOSED), eq(PaymentStatus.PAID), any(Limit.class)))
                .thenReturn(payments);
        when(jobRepository.findAllById(anyIterable())).thenReturn(jobs);
        when(jobApplicationRepository.findAllById(anyIterable())).thenReturn(applications);
        when(ownerRepository.findAllById(anyIterable())).thenReturn(List.of(
                Owner.builder().id(5L).storeName("가꿈 베이커리").build(),
                Owner.builder().id(6L).storeName("가꿈 카페").build()));
        when(specialtyRepository.findAllById(anyIterable())).thenReturn(List.of(
                Specialty.builder().id(11L).specialtyCategoryId(1L).name("포스터").build(),
                Specialty.builder().id(12L).specialtyCategoryId(1L).name("로고").build(),
                Specialty.builder().id(21L).specialtyCategoryId(2L).name("숏폼").build()));
        when(specialtyCategoryRepository.findAllById(anyIterable())).thenReturn(List.of(
                SpecialtyCategory.builder().id(1L).name("디자인").build(),
                SpecialtyCategory.builder().id(2L).name("영상").build()));
    }

    @Test
    @DisplayName("학생 본인의 프로필·학과·집계·특기·자격증·받은 리뷰·정산 완료 내역 17개 필드를 반환하고 학번은 입학연도 두 자리로 내린다")
    void returnsOwnInformation() throws Exception {
        givenStudent(UserRole.STUDENT, "https://cdn.gakkum.test/profile.png", "포스터를 잘 만듭니다.");
        when(proposalRepository.countByStudentProfileIdAndStatusNot(STUDENT_PROFILE_ID, ProposalStatus.CANCELLED))
                .thenReturn(2L);
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
        closedJob(42L, 5L, "가을 메뉴 포스터 디자인", null, "2026-09-27T10:00:00");
        closedJob(43L, 6L, "로고 리뉴얼", null, "2026-09-21T09:00:00");
        review(303L, 42L, 5, "수정 요청을 빠르게 반영해 주셨어요.", LocalDateTime.of(2026, 9, 28, 21, 30, 15));
        review(302L, 43L, 4, "결과물이 깔끔했어요.", LocalDateTime.of(2026, 9, 22, 8, 0));
        when(jobSpecialtyRepository.findByJobIdIn(any())).thenReturn(List.of(
                JobSpecialty.create(42L, 21L), JobSpecialty.create(42L, 11L), JobSpecialty.create(43L, 12L)));
        payments.add(paidPayment(42L, 91L, 50000L));
        application(91L, 42L, STUDENT_PROFILE_ID);

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(17))
                .andExpect(jsonPath("$.data.studentProfileId").value(7))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.gakkum.test/profile.png"))
                .andExpect(jsonPath("$.data.name").value("김광운"))
                .andExpect(jsonPath("$.data.university").value("광운대학교"))
                .andExpect(jsonPath("$.data.studentNumber").value("24"))
                .andExpect(jsonPath("$.data.major").value("컴퓨터정보공학부"))
                .andExpect(jsonPath("$.data.introduction").value("포스터를 잘 만듭니다."))
                .andExpect(jsonPath("$.data.portfolioUrl").value("https://portfolio.gakkum.test/kim"))
                .andExpect(jsonPath("$.data.proposalCount").value(2))
                .andExpect(jsonPath("$.data.completedJobCount").value(4))
                .andExpect(jsonPath("$.data.penaltyCount").value(1))
                // 대분류 ID·소분류 ID 오름차순
                .andExpect(jsonPath("$.data.specialtyCategories.length()").value(2))
                .andExpect(jsonPath("$.data.specialtyCategories[0].id").value(1))
                .andExpect(jsonPath("$.data.specialtyCategories[0].name").value("디자인"))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[0].id").value(11))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[0].name").value("포스터"))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[1].id").value(12))
                .andExpect(jsonPath("$.data.specialtyCategories[1].id").value(2))
                .andExpect(jsonPath("$.data.specialtyCategories[1].specialties[0].name").value("숏폼"))
                .andExpect(jsonPath("$.data.certificates.length()").value(2))
                .andExpect(jsonPath("$.data.certificates[0].certificateName").value("정보처리기사"))
                .andExpect(jsonPath("$.data.certificates[0].acquiredYear").value(2025))
                .andExpect(jsonPath("$.data.certificates[0].length()").value(2))
                .andExpect(jsonPath("$.data.reviews.length()").value(2))
                .andExpect(jsonPath("$.data.reviews[0].length()").value(6))
                .andExpect(jsonPath("$.data.reviews[0].jobTitle").value("가을 메뉴 포스터 디자인"))
                .andExpect(jsonPath("$.data.reviews[0].storeName").value("가꿈 베이커리"))
                .andExpect(jsonPath("$.data.reviews[0].rating").value(5))
                .andExpect(jsonPath("$.data.reviews[0].content").value("수정 요청을 빠르게 반영해 주셨어요."))
                .andExpect(jsonPath("$.data.reviews[0].createdAt").value("2026-09-28"))
                // 리뷰의 분류는 학생 특기가 아닌 해당 의뢰에 연결된 전체 분류다
                .andExpect(jsonPath("$.data.reviews[0].specialtyCategories.length()").value(2))
                .andExpect(jsonPath("$.data.reviews[0].specialtyCategories[0].specialties.length()").value(1))
                .andExpect(jsonPath("$.data.reviews[0].specialtyCategories[0].specialties[0].id").value(11))
                .andExpect(jsonPath("$.data.reviews[0].specialtyCategories[1].specialties[0].id").value(21))
                .andExpect(jsonPath("$.data.reviews[1].jobTitle").value("로고 리뉴얼"))
                .andExpect(jsonPath("$.data.reviews[1].storeName").value("가꿈 카페"))
                .andExpect(jsonPath("$.data.reviews[1].specialtyCategories.length()").value(1))
                .andExpect(jsonPath("$.data.reviews[1].specialtyCategories[0].specialties[0].name").value("로고"))
                .andExpect(jsonPath("$.data.settlements.length()").value(1));

        // 다른 학생의 데이터를 섞지 않도록 모든 조회가 본인 프로필 ID로만 나간다
        verify(reviewRepository).findByStudentProfileId(STUDENT_PROFILE_ID);
        verify(reviewRepository).countByStudentProfileId(STUDENT_PROFILE_ID);
        verify(reviewRepository).findAverageRatingByStudentProfileId(STUDENT_PROFILE_ID);
        // 리뷰와 달리 정산 완료 내역은 최신 세 개만 조회한다
        ArgumentCaptor<Limit> settlementLimit = ArgumentCaptor.forClass(Limit.class);
        verify(paymentRepository).findLatestCompletedByStudentProfileId(
                eq(STUDENT_PROFILE_ID), eq(JobStatus.CLOSED), eq(PaymentStatus.PAID), settlementLimit.capture());
        assertThat(settlementLimit.getValue().max()).isEqualTo(3);
    }

    @Test
    @DisplayName("제안 수는 취소한 제안을 뺀 집계를, 완료 수는 CLOSED 의뢰 집계를 쓰고 취소 포함 집계는 호출하지 않는다")
    void countsProposalsExcludingCancelledAndClosedJobsOnly() throws Exception {
        givenStudent(UserRole.STUDENT, null, null);
        when(proposalRepository.countByStudentProfileId(STUDENT_PROFILE_ID)).thenReturn(5L);
        when(proposalRepository.countByStudentProfileIdAndStatusNot(STUDENT_PROFILE_ID, ProposalStatus.CANCELLED))
                .thenReturn(3L);
        when(jobRepository.countBySelectedStudentProfileIdAndStatus(STUDENT_PROFILE_ID, JobStatus.CLOSED))
                .thenReturn(2L);

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposalCount").value(3))
                .andExpect(jsonPath("$.data.completedJobCount").value(2));
        verify(proposalRepository, never()).countByStudentProfileId(STUDENT_PROFILE_ID);
    }

    @Test
    @DisplayName("받은 리뷰는 개수 제한 없이 모두 작성 시각 내림차순, 같은 시각은 리뷰 ID 내림차순으로 내리고 작성 시각이 없는 리뷰는 마지막에 둔다")
    void returnsAllReviewsInOrder() throws Exception {
        givenStudent(UserRole.STUDENT, null, null);
        closedJob(42L, 5L, "의뢰 A", null, "2026-09-27T10:00:00");
        closedJob(43L, 6L, "의뢰 B", null, "2026-09-21T09:00:00");
        closedJob(44L, 5L, "의뢰 C", null, "2026-09-20T09:00:00");
        closedJob(45L, 6L, "의뢰 D", null, "2026-09-19T09:00:00");
        closedJob(46L, 5L, "의뢰 E", null, "2026-09-18T09:00:00");
        closedJob(47L, 6L, "의뢰 F", null, "2026-09-17T09:00:00");
        // 저장소가 돌려주는 순서와 무관하게 정렬한다
        review(301L, 42L, 3, "작성 시각 없음, 낮은 ID", null);
        review(303L, 43L, 4, "같은 시각, 낮은 ID", LocalDateTime.of(2026, 9, 22, 8, 0));
        review(306L, 44L, 5, "작성 시각 없음, 높은 ID", null);
        review(302L, 45L, 5, "가장 오래된 리뷰", LocalDateTime.of(2026, 9, 20, 8, 0));
        review(305L, 46L, 4, "가장 최근 리뷰", LocalDateTime.of(2026, 9, 28, 21, 30));
        review(304L, 47L, 5, "같은 시각, 높은 ID", LocalDateTime.of(2026, 9, 22, 8, 0));

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews.length()").value(6))
                .andExpect(jsonPath("$.data.reviews[0].jobTitle").value("의뢰 E"))
                .andExpect(jsonPath("$.data.reviews[1].jobTitle").value("의뢰 F"))
                .andExpect(jsonPath("$.data.reviews[2].jobTitle").value("의뢰 B"))
                .andExpect(jsonPath("$.data.reviews[3].jobTitle").value("의뢰 D"))
                .andExpect(jsonPath("$.data.reviews[4].jobTitle").value("의뢰 C"))
                .andExpect(jsonPath("$.data.reviews[5].jobTitle").value("의뢰 A"))
                .andExpect(jsonPath("$.data.reviews[3].createdAt").value("2026-09-20"))
                // 작성 시각이 없는 기존 데이터는 날짜 없이 내린다
                .andExpect(jsonPath("$.data.reviews[5].length()").value(6))
                .andExpect(jsonPath("$.data.reviews[5].createdAt").value(nullValue()))
                .andExpect(jsonPath("$.data.reviews[5].specialtyCategories.length()").value(0));
    }

    @Test
    @DisplayName("리뷰 수와 평균 별점은 받은 전체 리뷰를 따로 집계한 값이고 평균은 소수 첫째 자리로 반올림한다")
    void countsAndAveragesAllReviews() throws Exception {
        givenStudent(UserRole.STUDENT, null, null);
        closedJob(42L, 5L, "의뢰 A", null, "2026-09-27T10:00:00");
        closedJob(43L, 6L, "의뢰 B", null, "2026-09-21T09:00:00");
        closedJob(44L, 5L, "의뢰 C", null, "2026-09-20T09:00:00");
        closedJob(45L, 6L, "의뢰 D", null, "2026-09-19T09:00:00");
        review(304L, 42L, 5, "a", LocalDateTime.of(2026, 9, 28, 21, 30));
        review(303L, 43L, 4, "b", LocalDateTime.of(2026, 9, 22, 8, 0));
        review(302L, 44L, 4, "c", LocalDateTime.of(2026, 9, 21, 8, 0));
        review(301L, 45L, 4, "d", LocalDateTime.of(2026, 9, 20, 8, 0));
        when(reviewRepository.countByStudentProfileId(STUDENT_PROFILE_ID)).thenReturn(4L);
        when(reviewRepository.findAverageRatingByStudentProfileId(STUDENT_PROFILE_ID)).thenReturn(4.25);

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews.length()").value(4))
                .andExpect(jsonPath("$.data.reviewCount").value(4))
                .andExpect(jsonPath("$.data.averageRating").value(4.3));
    }

    @Test
    @DisplayName("정산 완료 내역은 일반·제안 의뢰를 함께 정산 내역 조회와 같은 6개 필드로 내리고 금액은 결제 금액, 정산일은 의뢰 완료 시각의 한국 날짜다")
    void returnsSettledItemsForApplicationAndProposalJobs() throws Exception {
        givenStudent(UserRole.STUDENT, null, null);
        // UTC 23:59:59는 한국 시간으로 다음 날 오전이다
        closedJob(45L, 6L, "제안으로 만든 의뢰", 77L, "2026-09-29T23:59:59");
        closedJob(42L, 5L, "가을 메뉴 포스터 디자인", null, "2026-09-27T00:00:00");
        payments.add(paidProposalPayment(77L, 45L, 80000L));
        payments.add(paidPayment(42L, 91L, 50000L));
        application(91L, 42L, STUDENT_PROFILE_ID);

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settlements.length()").value(2))
                .andExpect(jsonPath("$.data.settlements[0].length()").value(6))
                .andExpect(jsonPath("$.data.settlements[0].jobId").value(45))
                .andExpect(jsonPath("$.data.settlements[0].title").value("제안으로 만든 의뢰"))
                .andExpect(jsonPath("$.data.settlements[0].amount").value(80000))
                .andExpect(jsonPath("$.data.settlements[0].settledDate").value("2026-09-30"))
                .andExpect(jsonPath("$.data.settlements[0].storeName").value("가꿈 카페"))
                .andExpect(jsonPath("$.data.settlements[0].status").value("SETTLED"))
                .andExpect(jsonPath("$.data.settlements[1].jobId").value(42))
                .andExpect(jsonPath("$.data.settlements[1].amount").value(50000))
                .andExpect(jsonPath("$.data.settlements[1].settledDate").value("2026-09-27"))
                .andExpect(jsonPath("$.data.settlements[1].storeName").value("가꿈 베이커리"))
                .andExpect(jsonPath("$.data.settlements[1].status").value("SETTLED"));
    }

    @ParameterizedTest
    @DisplayName("일반·제안 의뢰의 정산일은 UTC로 저장된 완료 시각의 한국 날짜다")
    @CsvSource({
            "2026-10-06T14:59:59, 2026-10-06",
            "2026-10-06T15:00:00, 2026-10-07",
            "2026-10-06T23:59:59, 2026-10-07",
            "2026-10-31T15:00:00, 2026-11-01",
            "2026-12-31T15:00:00, 2027-01-01"
    })
    void returnsKoreanSettledDate(String completedAtUtc, String expectedDate) throws Exception {
        givenStudent(UserRole.STUDENT, null, null);
        closedJob(45L, 6L, "제안으로 만든 의뢰", 77L, completedAtUtc);
        closedJob(42L, 5L, "가을 메뉴 포스터 디자인", null, completedAtUtc);
        payments.add(paidProposalPayment(77L, 45L, 80000L));
        payments.add(paidPayment(42L, 91L, 50000L));
        application(91L, 42L, STUDENT_PROFILE_ID);

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settlements[0].settledDate").value(expectedDate))
                .andExpect(jsonPath("$.data.settlements[1].settledDate").value(expectedDate));
    }

    @Test
    @DisplayName("리뷰가 정산 미리보기 개수보다 많아도 의뢰·지원서·매장·의뢰 소분류·분류 상세를 필요한 ID를 모두 모아 각각 한 번씩만 조회한다")
    void batchesRelatedQueries() throws Exception {
        givenStudent(UserRole.STUDENT, null, null);
        when(studentSpecialtyRepository.findByStudentProfileIdIn(List.of(STUDENT_PROFILE_ID))).thenReturn(List.of(
                StudentSpecialty.create(STUDENT_PROFILE_ID, 12L)));
        closedJob(42L, 5L, "의뢰 A", null, "2026-09-27T10:00:00");
        closedJob(43L, 6L, "의뢰 B", null, "2026-09-21T09:00:00");
        closedJob(44L, 5L, "의뢰 C", null, "2026-09-20T09:00:00");
        closedJob(45L, 6L, "제안으로 만든 의뢰", 77L, "2026-09-29T23:59:59");
        closedJob(46L, 6L, "의뢰 D", null, "2026-09-26T09:00:00");
        closedJob(47L, 5L, "의뢰 E", null, "2026-09-19T09:00:00");
        closedJob(48L, 6L, "의뢰 F", null, "2026-09-18T09:00:00");
        review(305L, 48L, 5, "e", LocalDateTime.of(2026, 9, 30, 8, 0));
        review(304L, 47L, 4, "d", LocalDateTime.of(2026, 9, 29, 8, 0));
        review(303L, 42L, 5, "a", LocalDateTime.of(2026, 9, 28, 21, 30));
        review(302L, 43L, 4, "b", LocalDateTime.of(2026, 9, 22, 8, 0));
        review(301L, 44L, 3, "c", LocalDateTime.of(2026, 9, 21, 8, 0));
        when(jobSpecialtyRepository.findByJobIdIn(any())).thenReturn(List.of(
                JobSpecialty.create(42L, 21L), JobSpecialty.create(42L, 11L), JobSpecialty.create(43L, 12L)));
        // 의뢰 42는 리뷰와 정산에 모두 걸려 있다
        payments.add(paidProposalPayment(77L, 45L, 80000L));
        payments.add(paidPayment(42L, 91L, 50000L));
        payments.add(paidPayment(46L, 92L, 50000L));
        application(91L, 42L, STUDENT_PROFILE_ID);
        application(92L, 46L, STUDENT_PROFILE_ID);

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews.length()").value(5))
                .andExpect(jsonPath("$.data.settlements.length()").value(3));

        verify(reviewRepository).findByStudentProfileId(STUDENT_PROFILE_ID);
        ArgumentCaptor<Limit> settlementLimit = ArgumentCaptor.forClass(Limit.class);
        verify(paymentRepository).findLatestCompletedByStudentProfileId(
                eq(STUDENT_PROFILE_ID), eq(JobStatus.CLOSED), eq(PaymentStatus.PAID), settlementLimit.capture());
        assertThat(settlementLimit.getValue().max()).isEqualTo(3);
        ArgumentCaptor<Iterable<Long>> jobIds = ArgumentCaptor.captor();
        verify(jobRepository).findAllById(jobIds.capture());
        assertThat(jobIds.getValue()).containsExactlyInAnyOrder(42L, 43L, 44L, 45L, 46L, 47L, 48L);
        ArgumentCaptor<Iterable<Long>> applicationIds = ArgumentCaptor.captor();
        verify(jobApplicationRepository).findAllById(applicationIds.capture());
        assertThat(applicationIds.getValue()).containsExactlyInAnyOrder(91L, 92L);
        ArgumentCaptor<Iterable<Long>> ownerProfileIds = ArgumentCaptor.captor();
        verify(ownerRepository).findAllById(ownerProfileIds.capture());
        assertThat(ownerProfileIds.getValue()).containsExactlyInAnyOrder(5L, 6L);
        // 의뢰 소분류는 리뷰가 달린 의뢰만 조회한다
        ArgumentCaptor<Collection<Long>> reviewJobIds = ArgumentCaptor.captor();
        verify(jobSpecialtyRepository).findByJobIdIn(reviewJobIds.capture());
        assertThat(reviewJobIds.getValue()).containsExactlyInAnyOrder(42L, 43L, 44L, 47L, 48L);
        // 학생 특기와 리뷰 의뢰의 소분류를 합쳐 분류 상세를 한 번에 조회한다
        ArgumentCaptor<Iterable<Long>> specialtyIds = ArgumentCaptor.captor();
        verify(specialtyRepository).findAllById(specialtyIds.capture());
        assertThat(specialtyIds.getValue()).containsExactlyInAnyOrder(11L, 12L, 21L);
        ArgumentCaptor<Iterable<Long>> categoryIds = ArgumentCaptor.captor();
        verify(specialtyCategoryRepository).findAllById(categoryIds.capture());
        assertThat(categoryIds.getValue()).containsExactlyInAnyOrder(1L, 2L);
        // 건별 단건 조회로 바뀌지 않았는지 확인한다
        verify(jobRepository, never()).findById(any());
        verify(jobApplicationRepository, never()).findById(any());
        verify(ownerRepository, never()).findById(any());
        verify(specialtyRepository, never()).findById(any());
        verify(specialtyCategoryRepository, never()).findById(any());
    }

    @Test
    @DisplayName("활동이 없는 학생은 개수 0, 평점 0.0, 빈 목록을 받고 사진 URL·소개·포트폴리오 URL은 저장된 null 그대로 받는다")
    void returnsEmptyDefaults() throws Exception {
        givenStudent(UserRole.STUDENT, null, null);

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(17))
                .andExpect(jsonPath("$.data.major").value("컴퓨터정보공학부"))
                .andExpect(jsonPath("$.data.profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.introduction").value(nullValue()))
                .andExpect(jsonPath("$.data.portfolioUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.proposalCount").value(0))
                .andExpect(jsonPath("$.data.completedJobCount").value(0))
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                .andExpect(jsonPath("$.data.averageRating").value(0.0))
                .andExpect(jsonPath("$.data.specialtyCategories.length()").value(0))
                .andExpect(jsonPath("$.data.certificates.length()").value(0))
                .andExpect(jsonPath("$.data.reviews.length()").value(0))
                .andExpect(jsonPath("$.data.settlements.length()").value(0));
    }

    @Test
    @DisplayName("사장님이 조회하면 403 STUDENT_403_ME를 반환하고 학생 데이터를 조회하지 않는다")
    void rejectsNonStudent() throws Exception {
        givenStudent(UserRole.OWNER, null, null);

        perform()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("STUDENT_403_ME"));
        verifyNoInteractions(studentRepository, reviewRepository, paymentRepository, jobRepository,
                proposalRepository);
    }

    @Test
    @DisplayName("잠겼거나 존재하지 않는 사용자는 401 COMMON_401로 거부한다")
    void rejectsInactiveUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        perform()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(studentRepository, reviewRepository, paymentRepository);
    }

    @Test
    @DisplayName("학생 역할인데 학생 프로필이 없으면 500 COMMON_500을 반환한다")
    void rejectsStudentWithoutProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).name("김광운").build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.empty());

        perform()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
        verifyNoInteractions(reviewRepository, paymentRepository);
    }

    @Test
    @DisplayName("정산 결제가 가리키는 지원서가 본인 것이 아니거나 제안이 의뢰의 제안과 다르면 500 COMMON_500을 반환한다")
    void rejectsBrokenSettlementReferences() throws Exception {
        givenStudent(UserRole.STUDENT, null, null);
        closedJob(42L, 5L, "가을 메뉴 포스터 디자인", null, "2026-09-27T00:00:00");
        payments.add(paidPayment(42L, 91L, 50000L));
        application(91L, 42L, OTHER_STUDENT_PROFILE_ID);

        perform()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));

        jobs.clear();
        payments.clear();
        closedJob(45L, 6L, "제안으로 만든 의뢰", 78L, "2026-09-29T23:59:59");
        payments.add(paidProposalPayment(77L, 45L, 80000L));

        perform()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("정산 완료 의뢰에 완료 시각이 없으면 500 COMMON_500을 반환한다")
    void rejectsSettlementWithoutCompletedAt() throws Exception {
        givenStudent(UserRole.STUDENT, null, null);
        closedJob(42L, 5L, "가을 메뉴 포스터 디자인", null, null);
        payments.add(paidPayment(42L, 91L, 50000L));
        application(91L, 42L, STUDENT_PROFILE_ID);

        perform()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    private ResultActions perform() throws Exception {
        return mockMvc.perform(get(URL).principal(authentication));
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

    private void closedJob(Long jobId, Long ownerProfileId, String title, Long proposalId, String completedAt) {
        jobs.add(Job.builder()
                .id(jobId)
                .ownerProfileId(ownerProfileId)
                .title(title)
                .status(JobStatus.CLOSED)
                .selectedStudentProfileId(STUDENT_PROFILE_ID)
                .proposalId(proposalId)
                .completedAt(completedAt == null ? null : LocalDateTime.parse(completedAt))
                .build());
    }

    private void review(Long reviewId, Long jobId, int rating, String content, LocalDateTime createdAt) {
        reviews.add(Review.builder()
                .id(reviewId)
                .jobId(jobId)
                .ownerProfileId(5L)
                .studentProfileId(STUDENT_PROFILE_ID)
                .positivePoints(List.of())
                .content(content)
                .rating(rating)
                .createdAt(createdAt)
                .build());
    }

    private void application(Long applicationId, Long jobId, Long studentProfileId) {
        applications.add(JobApplication.builder().id(applicationId).jobId(jobId).studentProfileId(studentProfileId)
                .build());
    }

    private Payment paidPayment(Long jobId, Long applicationId, Long amount) {
        Payment payment = Payment.pending(jobId, applicationId, OWNER_USER_ID, "order-" + jobId, amount,
                Instant.parse("2026-09-01T00:00:00Z"));
        payment.recordKakaoTid("T" + jobId);
        payment.approve(Instant.parse("2026-09-01T00:01:00Z"));
        return payment;
    }

    private Payment paidProposalPayment(Long proposalId, Long jobId, Long amount) {
        Payment payment = Payment.pendingForProposal(proposalId, OWNER_USER_ID, "order-" + jobId, amount, 1, null,
                Instant.parse("2026-09-01T00:00:00Z"));
        payment.recordKakaoTid("T" + jobId);
        payment.approve(Instant.parse("2026-09-01T00:01:00Z"));
        payment.linkJob(jobId);
        return payment;
    }
}
