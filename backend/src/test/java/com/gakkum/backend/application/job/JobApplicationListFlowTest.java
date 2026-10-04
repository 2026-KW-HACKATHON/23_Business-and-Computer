package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobRepository.StudentJobCount;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.review.repository.ReviewRepository;
import com.gakkum.backend.domain.review.repository.ReviewRepository.StudentAverageRating;
import com.gakkum.backend.domain.proposal.service.ProposalService;
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

@DisplayName("의뢰 지원자 목록 조회 전체 흐름 (GET /jobs/{jobId}/applications)")
class JobApplicationListFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final Long OWNER_PROFILE_ID = 5L;
    private static final Long JOB_ID = 42L;
    private static final LocalDateTime APPLIED_AT = LocalDateTime.of(2026, 10, 3, 10, 0);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final StudentSpecialtyRepository studentSpecialtyRepository = mock(StudentSpecialtyRepository.class);
    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final SpecialtyCategoryRepository specialtyCategoryRepository = mock(SpecialtyCategoryRepository.class);

    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JobFacade facade = new JobFacade(
                new UserService(userRepository, mock(JwtService.class)),
                new OwnerService(ownerRepository),
                new JobService(jobRepository, jobSpecialtyRepository, jobApplicationRepository,
                        mock(JobSubmissionRepository.class), Clock.systemUTC()),
                new SpecialtyCategoryService(specialtyCategoryRepository, specialtyRepository),
                new SpecialtyService(specialtyRepository, studentSpecialtyRepository),
                new StudentService(studentRepository),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class),
                mock(PaymentService.class),
                new ReviewService(reviewRepository),
                mock(CertificateService.class), mock(ProposalService.class));

        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("의뢰 정보와 대기 중 지원자의 모든 필드를 응답하고 sort를 생략하면 최신 지원순이다")
    void returnsJobAndApplicants() throws Exception {
        givenOwner();
        givenOpenJob();
        givenApplicants();

        getApplications(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.job.jobId").value(42))
                .andExpect(jsonPath("$.data.job.title").value("가게 홍보 포스터 제작"))
                .andExpect(jsonPath("$.data.job.budget").value(100000))
                .andExpect(jsonPath("$.data.job.draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.job.finalDeadline").value("2026-10-20"))
                .andExpect(jsonPath("$.data.job.specialtyCategories[*].id").value(contains(1, 2)))
                .andExpect(jsonPath("$.data.job.specialtyCategories[0].name").value("개발"))
                .andExpect(jsonPath("$.data.job.specialtyCategories[0].specialties[*].id").value(contains(11)))
                .andExpect(jsonPath("$.data.job.specialtyCategories[1].name").value("디자인"))
                .andExpect(jsonPath("$.data.job.specialtyCategories[1].specialties[0].id").value(21))
                .andExpect(jsonPath("$.data.job.specialtyCategories[1].specialties[0].name").value("포스터 디자인"))
                .andExpect(jsonPath("$.data.applicantCount").value(4))
                .andExpect(jsonPath("$.data.applicants.length()").value(4))
                .andExpect(jsonPath("$.data.applicants[*].jobApplicationId").value(contains(106, 107, 105, 104)))
                .andExpect(jsonPath("$.data.applicants[2].studentProfileId").value(7))
                .andExpect(jsonPath("$.data.applicants[2].profileImageUrl").value("https://example.com/7.jpg"))
                .andExpect(jsonPath("$.data.applicants[2].name").value("학생7"))
                .andExpect(jsonPath("$.data.applicants[2].studentNumber").value("2023000007"))
                .andExpect(jsonPath("$.data.applicants[2].major").value("소프트웨어학부"))
                .andExpect(jsonPath("$.data.applicants[2].averageRating").value(4.3))
                .andExpect(jsonPath("$.data.applicants[2].completedJobCount").value(3))
                .andExpect(jsonPath("$.data.applicants[2].content").value("포스터를 제작하겠습니다."))
                .andExpect(jsonPath("$.data.applicants[2].appliedAt").doesNotExist())
                // 의뢰에 없는 특기(12)까지 학생이 등록한 전체 특기를 대분류·소분류 ID 오름차순으로 내린다
                .andExpect(jsonPath("$.data.applicants[2].specialtyCategories[*].id").value(contains(1, 2)))
                .andExpect(jsonPath("$.data.applicants[2].specialtyCategories[0].specialties[*].id")
                        .value(contains(11, 12)))
                .andExpect(jsonPath("$.data.applicants[2].specialtyCategories[1].specialties[*].id")
                        .value(contains(21)));

        verify(jobApplicationRepository).findByJobIdInAndStatus(List.of(JOB_ID), JobApplicationStatus.PENDING);
        verify(jobRepository).countByStudentProfileIdsAndStatus(anyCollection(), eq(JobStatus.CLOSED));
    }

    @Test
    @DisplayName("리뷰·완료 의뢰·특기·사진·계획서가 없는 지원자는 0.0, 0, 빈 배열, null로 응답한다")
    void returnsDefaultsForApplicantWithoutData() throws Exception {
        givenOwner();
        givenOpenJob();
        givenApplicants();

        getApplications("LATEST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicants[0].jobApplicationId").value(106))
                .andExpect(jsonPath("$.data.applicants[0].studentProfileId").value(8))
                .andExpect(jsonPath("$.data.applicants[0].averageRating").value(0.0))
                .andExpect(jsonPath("$.data.applicants[0].completedJobCount").value(0))
                .andExpect(jsonPath("$.data.applicants[0].specialtyCategories").isArray())
                .andExpect(jsonPath("$.data.applicants[0].specialtyCategories").isEmpty())
                .andExpect(jsonPath("$.data.applicants[0].profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.applicants[0].content").value(nullValue()));
    }

    @Test
    @DisplayName("RATING은 표시되는 평균 별점 내림차순이고 동률이면 최신 지원순이며 지원 시각이 없으면 뒤에 둔다")
    void sortsByDisplayedRating() throws Exception {
        givenOwner();
        givenOpenJob();
        givenApplicants();

        // 105(4.25 → 4.3)와 104(4.3)는 표시 별점이 같아 지원 시각이 있는 105가 앞선다
        getApplications("RATING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicants[*].jobApplicationId").value(contains(105, 104, 106, 107)))
                .andExpect(jsonPath("$.data.applicants[*].averageRating").value(contains(4.3, 4.3, 0.0, 0.0)));
    }

    @Test
    @DisplayName("COMPLETED는 완료 건수 내림차순이고 건수와 지원 시각이 같으면 지원서 ID 내림차순이다")
    void sortsByCompletedJobCount() throws Exception {
        givenOwner();
        givenOpenJob();
        givenApplicants();

        getApplications("COMPLETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicants[*].jobApplicationId").value(contains(104, 107, 105, 106)))
                .andExpect(jsonPath("$.data.applicants[*].completedJobCount").value(contains(5, 3, 3, 0)));
    }

    @Test
    @DisplayName("지원자가 없으면 의뢰 정보와 applicantCount 0, 빈 배열을 응답하고 학생 정보·통계는 조회하지 않는다")
    void returnsEmptyApplicants() throws Exception {
        givenOwner();
        givenOpenJob();
        when(jobApplicationRepository.findByJobIdInAndStatus(List.of(JOB_ID), JobApplicationStatus.PENDING))
                .thenReturn(List.of());
        givenSpecialties();

        getApplications(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.job.jobId").value(42))
                .andExpect(jsonPath("$.data.job.specialtyCategories.length()").value(2))
                .andExpect(jsonPath("$.data.applicantCount").value(0))
                .andExpect(jsonPath("$.data.applicants").isArray())
                .andExpect(jsonPath("$.data.applicants").isEmpty());

        verifyNoInteractions(studentRepository, studentSpecialtyRepository, reviewRepository);
        verify(userRepository).findByUsernameAndIsLock(USERNAME, false);
        verifyNoMoreInteractions(userRepository);
        verify(jobRepository).findById(JOB_ID);
        verifyNoMoreInteractions(jobRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 30 })
    @DisplayName("지원자 수가 늘어도 Repository는 종류별로 한 번씩만 호출한다")
    void queriesEachRepositoryOnceRegardlessOfApplicantCount(int applicantCount) throws Exception {
        givenOwner();
        givenOpenJob();
        List<Long> studentIds = LongStream.rangeClosed(1, applicantCount).boxed().toList();
        when(jobApplicationRepository.findByJobIdInAndStatus(List.of(JOB_ID), JobApplicationStatus.PENDING))
                .thenReturn(studentIds.stream().map(id -> application(1000 + id, id, APPLIED_AT, "계획")).toList());
        when(studentRepository.findAllById(any())).thenReturn(
                studentIds.stream().map(id -> student(id, null)).toList());
        when(userRepository.findAllById(any())).thenReturn(
                studentIds.stream().map(JobApplicationListFlowTest::studentUser).toList());
        when(studentSpecialtyRepository.findByStudentProfileIdIn(anyCollection())).thenReturn(
                studentIds.stream().map(id -> StudentSpecialty.create(id, 12L)).toList());
        givenSpecialties();

        getApplications(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicantCount").value(applicantCount))
                .andExpect(jsonPath("$.data.applicants.length()").value(applicantCount));

        verify(userRepository).findByUsernameAndIsLock(USERNAME, false);
        verify(userRepository).findAllById(any());
        verify(ownerRepository).findByUserId(USER_ID);
        verify(jobRepository).findById(JOB_ID);
        verify(jobRepository).countByStudentProfileIdsAndStatus(studentIds, JobStatus.CLOSED);
        verify(jobSpecialtyRepository).findByJobIdIn(List.of(JOB_ID));
        verify(jobApplicationRepository).findByJobIdInAndStatus(List.of(JOB_ID), JobApplicationStatus.PENDING);
        verify(studentRepository).findAllById(any());
        verify(studentSpecialtyRepository).findByStudentProfileIdIn(studentIds);
        verify(reviewRepository).findAverageRatingsByStudentProfileIds(studentIds);
        verify(specialtyRepository).findAllById(any());
        verify(specialtyCategoryRepository).findAllById(any());
        verifyNoMoreInteractions(userRepository, ownerRepository, jobRepository, jobSpecialtyRepository,
                jobApplicationRepository, studentRepository, studentSpecialtyRepository, reviewRepository,
                specialtyRepository, specialtyCategoryRepository);
    }

    @Test
    @DisplayName("지원자가 참조하는 학생 프로필이 없으면 지원자를 빼지 않고 500을 응답한다")
    void failsWhenStudentIsMissing() throws Exception {
        givenOwner();
        givenOpenJob();
        givenApplicants();
        when(studentRepository.findAllById(any())).thenReturn(List.of(student(7L, null), student(8L, null)));

        getApplications(null)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("지원자 학생의 사용자 정보가 없으면 500을 응답한다")
    void failsWhenStudentUserIsMissing() throws Exception {
        givenOwner();
        givenOpenJob();
        givenApplicants();
        when(userRepository.findAllById(any())).thenReturn(List.of(studentUser(7L)));

        getApplications(null)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("지원자 학생이 등록한 특기 참조가 누락되면 500을 응답한다")
    void failsWhenStudentSpecialtyIsMissing() throws Exception {
        givenOwner();
        givenOpenJob();
        givenApplicants();
        when(studentSpecialtyRepository.findByStudentProfileIdIn(anyCollection()))
                .thenReturn(List.of(StudentSpecialty.create(7L, 99L)));

        getApplications(null)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Specialty not found: 99"));
    }

    @Test
    @DisplayName("잠겼거나 없는 사용자는 401을 응답하고 의뢰를 조회하지 않는다")
    void rejectsLockedUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        getApplications(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(ownerRepository, jobRepository, jobApplicationRepository);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "OWNER", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("사장님이 아닌 역할은 403을 응답하고 프로필과 의뢰를 조회하지 않는다")
    void rejectsNonOwnerRole(UserRole role) throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(user(role)));

        getApplications(null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_403_LIST_OWNER"));

        verifyNoInteractions(ownerRepository, jobRepository, jobApplicationRepository);
    }

    @Test
    @DisplayName("사장님 프로필이 없으면 OWNER_403을 응답하고 의뢰를 조회하지 않는다")
    void rejectsOwnerWithoutProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(user(UserRole.OWNER)));
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        getApplications(null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OWNER_403"));

        verifyNoInteractions(jobRepository, jobApplicationRepository);
    }

    @Test
    @DisplayName("없는 의뢰는 JOB_404를 응답한다")
    void rejectsMissingJob() throws Exception {
        givenOwner();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        getApplications(null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));

        verifyNoInteractions(jobApplicationRepository);
    }

    @Test
    @DisplayName("다른 사장님의 의뢰는 없는 의뢰와 같은 JOB_404를 응답하고 지원서를 조회하지 않는다")
    void rejectsOtherOwnersJob() throws Exception {
        givenOwner();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job(6L, JobStatus.OPEN)));

        getApplications(null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));

        verifyNoInteractions(jobSpecialtyRepository, jobApplicationRepository);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = "OPEN", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("본인 의뢰라도 모집 중이 아니면 409를 응답하고 지원서를 조회하지 않는다")
    void rejectsJobThatIsNotOpen(JobStatus jobStatus) throws Exception {
        givenOwner();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job(OWNER_PROFILE_ID, jobStatus)));

        getApplications(null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_409_LIST_STATUS"));

        verifyNoInteractions(jobSpecialtyRepository, jobApplicationRepository);
    }

    @Test
    @DisplayName("조회만 하므로 쓰기 잠금이 걸리는 의뢰 조회를 쓰지 않는다")
    void doesNotLockJob() throws Exception {
        givenOwner();
        givenOpenJob();
        givenApplicants();

        getApplications(null).andExpect(status().isOk());

        verify(jobRepository).findById(JOB_ID);
        verify(jobRepository).countByStudentProfileIdsAndStatus(anyCollection(), any());
        verifyNoMoreInteractions(jobRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-1", "abc" })
    @DisplayName("양수가 아니거나 숫자가 아닌 jobId는 400을 응답하고 사용자 조회를 시작하지 않는다")
    void rejectsInvalidJobId(String jobId) throws Exception {
        mockMvc.perform(get("/jobs/" + jobId + "/applications").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, jobRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "latest", "Rating", " LATEST", "POPULAR" })
    @DisplayName("빈 값·소문자·지원하지 않는 sort는 400을 응답하고 사용자 조회를 시작하지 않는다")
    void rejectsInvalidSort(String sort) throws Exception {
        getApplications(sort)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, jobRepository);
    }

    private ResultActions getApplications(String sort) throws Exception {
        MockHttpServletRequestBuilder request = get("/jobs/{jobId}/applications", JOB_ID).principal(authentication);
        if (sort != null) {
            request.param("sort", sort);
        }
        return mockMvc.perform(request);
    }

    private void givenOwner() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(user(UserRole.OWNER)));
        when(ownerRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(Owner.builder().id(OWNER_PROFILE_ID).build()));
    }

    private void givenOpenJob() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job(OWNER_PROFILE_ID, JobStatus.OPEN)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(JOB_ID))).thenReturn(List.of(
                JobSpecialty.create(JOB_ID, 21L), JobSpecialty.create(JOB_ID, 11L)));
    }

    /**
     * 지원서 105(학생 7): 10/3 지원, 평균 4.25, 완료 3건, 특기 21·12·11
     * 지원서 106(학생 8): 10/4 지원, 리뷰·완료·특기·사진·계획서 없음
     * 지원서 104(학생 9): 지원 시각 없음, 평균 4.3, 완료 5건
     * 지원서 107(학생 10): 105와 같은 시각에 지원, 리뷰 없음, 완료 3건
     */
    private void givenApplicants() {
        when(jobApplicationRepository.findByJobIdInAndStatus(List.of(JOB_ID), JobApplicationStatus.PENDING))
                .thenReturn(List.of(
                        application(105L, 7L, APPLIED_AT, "포스터를 제작하겠습니다."),
                        application(106L, 8L, APPLIED_AT.plusDays(1), null),
                        application(104L, 9L, null, "계획"),
                        application(107L, 10L, APPLIED_AT, "계획")));
        when(studentRepository.findAllById(any())).thenReturn(List.of(
                student(7L, "https://example.com/7.jpg"), student(8L, null), student(9L, null), student(10L, null)));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                studentUser(7L), studentUser(8L), studentUser(9L), studentUser(10L)));
        when(studentSpecialtyRepository.findByStudentProfileIdIn(anyCollection())).thenReturn(List.of(
                StudentSpecialty.create(7L, 21L), StudentSpecialty.create(7L, 12L), StudentSpecialty.create(7L, 11L)));
        when(reviewRepository.findAverageRatingsByStudentProfileIds(anyCollection())).thenReturn(List.of(
                averageRating(7L, 4.25), averageRating(9L, 4.3)));
        when(jobRepository.countByStudentProfileIdsAndStatus(anyCollection(), any())).thenReturn(List.of(
                jobCount(7L, 3L), jobCount(9L, 5L), jobCount(10L, 3L)));
        givenSpecialties();
    }

    private void givenSpecialties() {
        when(specialtyRepository.findAllById(any())).thenAnswer(invocation -> {
            List<Specialty> found = new ArrayList<>();
            for (Object id : (Iterable<?>) invocation.getArgument(0)) {
                if (id.equals(11L)) {
                    found.add(specialty(11L, 1L, "백엔드"));
                } else if (id.equals(12L)) {
                    found.add(specialty(12L, 1L, "프론트엔드"));
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

    private static Job job(Long ownerProfileId, JobStatus status) {
        return Job.builder()
                .id(JOB_ID)
                .ownerProfileId(ownerProfileId)
                .title("가게 홍보 포스터 제작")
                .budget(100000L)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .revisionCount(2)
                .status(status)
                .build();
    }

    private static JobApplication application(Long id, Long studentProfileId, LocalDateTime createdAt, String content) {
        return JobApplication.builder()
                .id(id)
                .jobId(JOB_ID)
                .studentProfileId(studentProfileId)
                .content(content)
                .status(JobApplicationStatus.PENDING)
                .createdAt(createdAt)
                .build();
    }

    private static Student student(Long id, String profileImageUrl) {
        return Student.builder()
                .id(id)
                .userId("USER_" + id)
                .studentNumber(String.format("2023%06d", id))
                .major("소프트웨어학부")
                .profileImageUrl(profileImageUrl)
                .build();
    }

    private static User studentUser(Long studentProfileId) {
        return User.builder()
                .id("USER_" + studentProfileId)
                .name("학생" + studentProfileId)
                .role(UserRole.STUDENT)
                .isLock(false)
                .build();
    }

    private static Specialty specialty(Long id, Long categoryId, String name) {
        return Specialty.builder().id(id).specialtyCategoryId(categoryId).name(name).build();
    }

    private static StudentAverageRating averageRating(Long studentProfileId, Double average) {
        return new StudentAverageRating() {
            @Override
            public Long getStudentProfileId() {
                return studentProfileId;
            }

            @Override
            public Double getAverageRating() {
                return average;
            }
        };
    }

    private static StudentJobCount jobCount(Long studentProfileId, Long count) {
        return new StudentJobCount() {
            @Override
            public Long getStudentProfileId() {
                return studentProfileId;
            }

            @Override
            public Long getJobCount() {
                return count;
            }
        };
    }
}
