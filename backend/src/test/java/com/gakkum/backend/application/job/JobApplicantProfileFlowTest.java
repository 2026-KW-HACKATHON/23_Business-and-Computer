package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.certificate.entity.StudentCertificate;
import com.gakkum.backend.domain.certificate.repository.StudentCertificateRepository;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
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

@DisplayName("의뢰 지원자 프로필 조회 전체 흐름 (GET /jobs/{jobId}/applications/{jobApplicationId}/profile)")
class JobApplicantProfileFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB7S";
    private static final Long OWNER_PROFILE_ID = 5L;
    private static final Long OTHER_OWNER_PROFILE_ID = 6L;
    private static final Long JOB_ID = 42L;
    private static final Long APPLICATION_ID = 105L;
    private static final Long STUDENT_PROFILE_ID = 7L;
    private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 9, 30, 23, 30);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final StudentSpecialtyRepository studentSpecialtyRepository = mock(StudentSpecialtyRepository.class);
    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final SpecialtyCategoryRepository specialtyCategoryRepository = mock(SpecialtyCategoryRepository.class);
    private final StudentCertificateRepository studentCertificateRepository =
            mock(StudentCertificateRepository.class);
    private final ProposalRepository proposalRepository = mock(ProposalRepository.class);

    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JobFacade facade = new JobFacade(
                new UserService(userRepository, mock(JwtService.class)),
                new OwnerService(ownerRepository),
                new JobService(jobRepository, mock(JobSpecialtyRepository.class), jobApplicationRepository,
                        mock(JobSubmissionRepository.class), Clock.systemUTC()),
                new SpecialtyCategoryService(specialtyCategoryRepository, specialtyRepository),
                new SpecialtyService(specialtyRepository, studentSpecialtyRepository),
                new StudentService(studentRepository),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class),
                mock(PaymentService.class),
                new ReviewService(reviewRepository),
                new CertificateService(studentCertificateRepository),
                new ProposalService(proposalRepository, mock(ProposalSpecialtyRepository.class)));

        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("모집 중 의뢰의 대기 지원자에 대해 학생 정보·통계·전체 특기·자격증·포트폴리오·패널티 횟수·리뷰를 응답한다")
    void returnsApplicantProfile() throws Exception {
        givenOwner();
        givenJob(JobStatus.OPEN, null);
        givenApplication(JobApplicationStatus.PENDING);
        givenStudent("https://example.com/portfolio", 3);
        givenActivity();

        getProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.student.studentProfileId").value(7))
                .andExpect(jsonPath("$.data.student.name").value("김가꿈"))
                .andExpect(jsonPath("$.data.student.university").value("광운대학교"))
                .andExpect(jsonPath("$.data.student.major").value("소프트웨어학부"))
                .andExpect(jsonPath("$.data.student.studentNumber").value("2023000007"))
                .andExpect(jsonPath("$.data.proposalCount").value(12))
                .andExpect(jsonPath("$.data.completedJobCount").value(5))
                // 학생이 등록한 전체 특기를 대분류·소분류 ID 오름차순으로 내린다
                .andExpect(jsonPath("$.data.specialtyCategories[*].id").value(contains(1, 2)))
                .andExpect(jsonPath("$.data.specialtyCategories[0].name").value("개발"))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[*].id").value(contains(11, 12)))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[0].name").value("웹 개발"))
                .andExpect(jsonPath("$.data.specialtyCategories[1].specialties[*].id").value(contains(21)))
                // Repository가 정렬해 돌려준 순서를 그대로 내린다
                .andExpect(jsonPath("$.data.certificates.length()").value(2))
                .andExpect(jsonPath("$.data.certificates[0].certificateName").value("정보처리기사"))
                .andExpect(jsonPath("$.data.certificates[0].acquiredYear").value(2025))
                .andExpect(jsonPath("$.data.certificates[1].certificateName").value("SQLD"))
                .andExpect(jsonPath("$.data.certificates[0].issuingOrganization").doesNotExist())
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

        verify(proposalRepository).countByStudentProfileId(STUDENT_PROFILE_ID);
        verify(jobRepository).countBySelectedStudentProfileIdAndStatus(STUDENT_PROFILE_ID, JobStatus.CLOSED);
        verify(reviewRepository).findByStudentProfileId(STUDENT_PROFILE_ID);
        verify(studentCertificateRepository)
                .findByStudentProfileIdOrderByAcquiredYearDescIdDesc(STUDENT_PROFILE_ID);
        // 리뷰가 참조하는 의뢰 전부와 그 의뢰의 사장님 전부를 중복 없이 조회한다
        verify(jobRepository).findAllById(exactly(Set.of(201L, 202L, 203L, 204L)));
        verify(ownerRepository).findAllById(exactly(Set.of(OWNER_PROFILE_ID, OTHER_OWNER_PROFILE_ID)));
    }

    @Test
    @DisplayName("특기·자격증·제안·완료 의뢰·리뷰·포트폴리오·패널티가 없으면 빈 배열, 0, null로 응답한다")
    void returnsDefaultsForStudentWithoutData() throws Exception {
        givenOwner();
        givenJob(JobStatus.OPEN, null);
        givenApplication(JobApplicationStatus.PENDING);
        givenStudent(null);

        getProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.studentProfileId").value(7))
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
    @MethodSource("matchedOrClosedWithEveryApplicationStatus")
    @DisplayName("매칭·완료된 의뢰는 선정된 학생의 지원서면 지원 상태와 무관하게 프로필을 조회할 수 있다")
    void returnsSelectedStudentProfileAfterMatching(JobStatus jobStatus, JobApplicationStatus applicationStatus)
            throws Exception {
        givenOwner();
        givenJob(jobStatus, STUDENT_PROFILE_ID);
        givenApplication(applicationStatus);
        givenStudent(null);

        getProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.studentProfileId").value(7));
    }

    @ParameterizedTest
    @MethodSource("matchedOrClosedWithEveryApplicationStatus")
    @DisplayName("매칭·완료된 의뢰의 선정되지 않은 학생 지원서는 수락 상태여도 JOB_APPLICATION_404를 응답하고 학생 정보를 조회하지 않는다")
    void rejectsUnselectedStudentAfterMatching(JobStatus jobStatus, JobApplicationStatus applicationStatus)
            throws Exception {
        givenOwner();
        givenJob(jobStatus, 8L);
        givenApplication(applicationStatus);

        getProfile()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_404"));

        verifyNoStudentDataAccess();
    }

    @ParameterizedTest
    @EnumSource(value = JobApplicationStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("모집 중 의뢰의 대기 상태가 아닌 지원서는 JOB_APPLICATION_404를 응답한다")
    void rejectsNonPendingApplicationOfOpenJob(JobApplicationStatus applicationStatus) throws Exception {
        givenOwner();
        givenJob(JobStatus.OPEN, null);
        givenApplication(applicationStatus);

        getProfile()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_404"));

        verifyNoStudentDataAccess();
    }

    @Test
    @DisplayName("없는 지원서는 JOB_APPLICATION_404를 응답한다")
    void rejectsMissingApplication() throws Exception {
        givenOwner();
        givenJob(JobStatus.OPEN, null);
        when(jobApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());

        getProfile()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_404"));

        verifyNoStudentDataAccess();
    }

    @Test
    @DisplayName("다른 의뢰의 지원서는 없는 지원서와 같은 JOB_APPLICATION_404를 응답한다")
    void rejectsApplicationOfOtherJob() throws Exception {
        givenOwner();
        givenJob(JobStatus.OPEN, null);
        when(jobApplicationRepository.findById(APPLICATION_ID))
                .thenReturn(Optional.of(application(43L, JobApplicationStatus.PENDING)));

        getProfile()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_404"));

        verifyNoStudentDataAccess();
    }

    @Test
    @DisplayName("취소된 본인 의뢰는 409를 응답하고 지원서와 학생 정보를 조회하지 않는다")
    void rejectsCancelledJob() throws Exception {
        givenOwner();
        givenJob(JobStatus.CANCELLED, STUDENT_PROFILE_ID);

        getProfile()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_409_PROFILE_STATUS"));

        verifyNoInteractions(jobApplicationRepository);
        verifyNoStudentDataAccess();
    }

    @Test
    @DisplayName("없는 의뢰는 JOB_404를 응답한다")
    void rejectsMissingJob() throws Exception {
        givenOwner();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        getProfile()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));

        verifyNoInteractions(jobApplicationRepository);
    }

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    @DisplayName("다른 사장님의 의뢰는 상태와 무관하게 없는 의뢰와 같은 JOB_404를 응답하고 지원서를 조회하지 않는다")
    void rejectsOtherOwnersJob(JobStatus jobStatus) throws Exception {
        givenOwner();
        when(jobRepository.findById(JOB_ID))
                .thenReturn(Optional.of(job(JOB_ID, OTHER_OWNER_PROFILE_ID, jobStatus, STUDENT_PROFILE_ID, "의뢰")));

        getProfile()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));

        verifyNoInteractions(jobApplicationRepository);
        verifyNoStudentDataAccess();
    }

    @Test
    @DisplayName("잠겼거나 없는 사용자는 401을 응답하고 의뢰를 조회하지 않는다")
    void rejectsLockedUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        getProfile()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(ownerRepository, jobRepository, jobApplicationRepository);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "OWNER", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("사장님이 아닌 역할은 403을 응답하고 프로필과 의뢰를 조회하지 않는다")
    void rejectsNonOwnerRole(UserRole role) throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(user(role)));

        getProfile()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_403_PROFILE_OWNER"));

        verifyNoInteractions(ownerRepository, jobRepository, jobApplicationRepository);
    }

    @Test
    @DisplayName("사장님 프로필이 없으면 OWNER_403을 응답하고 의뢰를 조회하지 않는다")
    void rejectsOwnerWithoutProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(user(UserRole.OWNER)));
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        getProfile()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OWNER_403"));

        verifyNoInteractions(jobRepository, jobApplicationRepository);
    }

    @ParameterizedTest
    @CsvSource({ "0,105", "-1,105", "abc,105", "42,0", "42,-1", "42,abc" })
    @DisplayName("양수가 아니거나 숫자가 아닌 ID는 400을 응답하고 사용자 조회를 시작하지 않는다")
    void rejectsInvalidIds(String jobId, String jobApplicationId) throws Exception {
        mockMvc.perform(get("/jobs/" + jobId + "/applications/" + jobApplicationId + "/profile")
                        .principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, jobRepository);
    }

    @Test
    @DisplayName("지원서가 참조하는 학생 프로필이 없으면 500을 응답한다")
    void failsWhenStudentIsMissing() throws Exception {
        givenOwner();
        givenJob(JobStatus.OPEN, null);
        givenApplication(JobApplicationStatus.PENDING);
        when(studentRepository.findById(STUDENT_PROFILE_ID)).thenReturn(Optional.empty());

        getProfile()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("학생의 사용자 정보가 없으면 500을 응답한다")
    void failsWhenStudentUserIsMissing() throws Exception {
        givenOwner();
        givenJob(JobStatus.OPEN, null);
        givenApplication(JobApplicationStatus.PENDING);
        givenStudent(null);
        when(userRepository.findById(STUDENT_USER_ID)).thenReturn(Optional.empty());

        getProfile()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("학생이 등록한 특기 참조가 누락되면 500을 응답한다")
    void failsWhenStudentSpecialtyIsMissing() throws Exception {
        givenOwner();
        givenJob(JobStatus.OPEN, null);
        givenApplication(JobApplicationStatus.PENDING);
        givenStudent(null);
        givenSpecialties();
        when(studentSpecialtyRepository.findByStudentProfileIdIn(anyCollection()))
                .thenReturn(List.of(StudentSpecialty.create(STUDENT_PROFILE_ID, 99L)));

        getProfile()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Specialty not found: 99"));
    }

    @Test
    @DisplayName("리뷰가 참조하는 의뢰가 없으면 리뷰를 빼지 않고 500을 응답한다")
    void failsWhenReviewedJobIsMissing() throws Exception {
        givenOwner();
        givenJob(JobStatus.OPEN, null);
        givenApplication(JobApplicationStatus.PENDING);
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
        givenJob(JobStatus.OPEN, null);
        givenApplication(JobApplicationStatus.PENDING);
        givenStudent(null);
        givenActivity();
        givenOwnersFoundById(List.of(owner(OWNER_PROFILE_ID, "월계카페")));

        getProfile()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 30 })
    @DisplayName("리뷰 수가 늘어도 Repository는 종류별로 한 번씩만 호출하고 쓰기 잠금이 걸리는 의뢰 조회를 쓰지 않는다")
    void queriesEachRepositoryOnceRegardlessOfReviewCount(int reviewCount) throws Exception {
        givenOwner();
        givenJob(JobStatus.OPEN, null);
        givenApplication(JobApplicationStatus.PENDING);
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
        // 리뷰한 의뢰가 하나면 홀수 ID라 다른 사장님의 의뢰뿐이다
        Set<Long> reviewOwnerIds = reviewCount == 1
                ? Set.of(OTHER_OWNER_PROFILE_ID)
                : Set.of(OWNER_PROFILE_ID, OTHER_OWNER_PROFILE_ID);
        when(studentSpecialtyRepository.findByStudentProfileIdIn(anyCollection()))
                .thenReturn(List.of(StudentSpecialty.create(STUDENT_PROFILE_ID, 11L)));
        givenSpecialties();

        getProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(reviewCount))
                .andExpect(jsonPath("$.data.reviews.length()").value(reviewCount));

        verify(userRepository).findByUsernameAndIsLock(USERNAME, false);
        verify(userRepository).findById(STUDENT_USER_ID);
        verify(ownerRepository).findByUserId(USER_ID);
        verify(ownerRepository).findAllById(exactly(reviewOwnerIds));
        verify(jobRepository).findById(JOB_ID);
        verify(jobRepository).findAllById(exactly(Set.copyOf(reviewedJobIds)));
        verify(jobRepository).countBySelectedStudentProfileIdAndStatus(STUDENT_PROFILE_ID, JobStatus.CLOSED);
        verify(jobApplicationRepository).findById(APPLICATION_ID);
        verify(studentRepository).findById(STUDENT_PROFILE_ID);
        verify(studentSpecialtyRepository).findByStudentProfileIdIn(List.of(STUDENT_PROFILE_ID));
        verify(specialtyRepository).findAllById(any());
        verify(specialtyCategoryRepository).findAllById(any());
        verify(reviewRepository).findByStudentProfileId(STUDENT_PROFILE_ID);
        verify(studentCertificateRepository)
                .findByStudentProfileIdOrderByAcquiredYearDescIdDesc(STUDENT_PROFILE_ID);
        verify(proposalRepository).countByStudentProfileId(STUDENT_PROFILE_ID);
        verifyNoMoreInteractions(userRepository, ownerRepository, jobRepository, jobApplicationRepository,
                studentRepository, studentSpecialtyRepository, specialtyRepository, specialtyCategoryRepository,
                reviewRepository, studentCertificateRepository, proposalRepository);
    }

    private static Stream<Arguments> matchedOrClosedWithEveryApplicationStatus() {
        return Stream.of(JobStatus.MATCHED, JobStatus.CLOSED)
                .flatMap(jobStatus -> Arrays.stream(JobApplicationStatus.values())
                        .map(applicationStatus -> Arguments.of(jobStatus, applicationStatus)));
    }

    private ResultActions getProfile() throws Exception {
        return mockMvc.perform(get("/jobs/{jobId}/applications/{jobApplicationId}/profile", JOB_ID, APPLICATION_ID)
                .principal(authentication));
    }

    private void verifyNoStudentDataAccess() {
        verifyNoInteractions(studentRepository, studentSpecialtyRepository, reviewRepository,
                studentCertificateRepository, proposalRepository);
    }

    private void givenOwner() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(user(UserRole.OWNER)));
        when(ownerRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(Owner.builder().id(OWNER_PROFILE_ID).build()));
    }

    private void givenJob(JobStatus status, Long selectedStudentProfileId) {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(
                job(JOB_ID, OWNER_PROFILE_ID, status, selectedStudentProfileId, "가게 홍보 포스터 제작")));
    }

    private void givenApplication(JobApplicationStatus status) {
        when(jobApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application(JOB_ID, status)));
    }

    // 패널티 횟수를 지정하지 않으면 학생 생성 시 기본값을 그대로 쓴다
    private void givenStudent(String portfolioUrl) {
        givenStudentFound(Student.builder()
                .id(STUDENT_PROFILE_ID)
                .userId(STUDENT_USER_ID)
                .university("광운대학교")
                .studentNumber("2023000007")
                .major("소프트웨어학부")
                .portfolioUrl(portfolioUrl)
                .build());
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
                .build());
    }

    private void givenStudentFound(Student student) {
        when(studentRepository.findById(STUDENT_PROFILE_ID)).thenReturn(Optional.of(student));
        when(userRepository.findById(STUDENT_USER_ID)).thenReturn(Optional.of(User.builder()
                .id(STUDENT_USER_ID)
                .name("김가꿈")
                .role(UserRole.STUDENT)
                .isLock(false)
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
                        StudentCertificate.create(STUDENT_PROFILE_ID, "정보처리기사", 2025, "한국산업인력공단"),
                        StudentCertificate.create(STUDENT_PROFILE_ID, "SQLD", 2024, "한국데이터산업진흥원")));
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

    private static User user(UserRole role) {
        return User.builder().id(USER_ID).username(USERNAME).role(role).isLock(false).build();
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

    private static JobApplication application(Long jobId, JobApplicationStatus status) {
        return JobApplication.builder()
                .id(APPLICATION_ID)
                .jobId(jobId)
                .studentProfileId(STUDENT_PROFILE_ID)
                .status(status)
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
