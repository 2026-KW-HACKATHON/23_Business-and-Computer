package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
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
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.repository.StudentSpecialtyRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@DisplayName("보낸 의뢰 목록 조회 전체 흐름 (GET /me/jobs?status=OPEN)")
class JobOpenListFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final SpecialtyCategoryRepository specialtyCategoryRepository = mock(SpecialtyCategoryRepository.class);
    private final StudentSpecialtyRepository studentSpecialtyRepository = mock(StudentSpecialtyRepository.class);
    private final JwtService jwtService = mock(JwtService.class);

    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, jwtService);
        OwnerService ownerService = new OwnerService(ownerRepository);
        SpecialtyService specialtyService = new SpecialtyService(specialtyRepository, studentSpecialtyRepository);
        JobService jobService = new JobService(
                jobRepository, jobSpecialtyRepository, jobApplicationRepository,
                mock(JobSubmissionRepository.class), specialtyService);
        SpecialtyCategoryService specialtyCategoryService =
                new SpecialtyCategoryService(specialtyCategoryRepository, specialtyRepository);
        JobFacade facade = new JobFacade(userService, ownerService, jobService,
                specialtyCategoryService, mock(StudentService.class));

        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("본인의 OPEN 의뢰와 대분류별 특기, 대기 중 지원 수를 응답한다")
    void returnsOpenJobsThroughAllLayers() throws Exception {
        givenOwner();
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.OPEN))
                .thenReturn(List.of(job(42L, "웹사이트 제작"), job(41L, "포스터 제작")));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L, 41L))).thenReturn(List.of(
                JobSpecialty.create(42L, 12L),
                JobSpecialty.create(41L, 21L),
                JobSpecialty.create(42L, 21L),
                JobSpecialty.create(42L, 11L)));
        when(jobApplicationRepository.findByJobIdInAndStatus(List.of(42L, 41L), JobApplicationStatus.PENDING))
                .thenReturn(List.of(application(42L), application(42L), application(42L)));
        when(specialtyRepository.findAllById(any())).thenReturn(List.of(
                specialty(11L, 1L, "백엔드"),
                specialty(12L, 1L, "프론트엔드"),
                specialty(21L, 2L, "디자인")));
        when(specialtyCategoryRepository.findAllById(any())).thenReturn(List.of(
                category(2L, "디자인"), category(1L, "개발")));

        getOpenJobs("OPEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.jobs.length()").value(2))
                .andExpect(jsonPath("$.data.jobs[0].jobId").value(42))
                .andExpect(jsonPath("$.data.jobs[0].title").value("웹사이트 제작"))
                .andExpect(jsonPath("$.data.jobs[0].draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.jobs[0].finalDeadline").value("2026-10-20"))
                .andExpect(jsonPath("$.data.jobs[0].revisionCount").value(2))
                .andExpect(jsonPath("$.data.jobs[0].applicantCount").value(3))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].id").value(1))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].name").value("개발"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].specialties[0].id").value(11))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].specialties[0].name").value("백엔드"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].specialties[1].id").value(12))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].specialties[1].name").value("프론트엔드"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[1].id").value(2))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[1].name").value("디자인"))
                .andExpect(jsonPath("$.data.jobs[1].jobId").value(41))
                .andExpect(jsonPath("$.data.jobs[1].title").value("포스터 제작"))
                .andExpect(jsonPath("$.data.jobs[1].draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.jobs[1].finalDeadline").value("2026-10-20"))
                .andExpect(jsonPath("$.data.jobs[1].applicantCount").value(0))
                .andExpect(jsonPath("$.data.jobs[1].specialtyCategories[0].id").value(2))
                .andExpect(jsonPath("$.data.jobs[1].specialtyCategories[0].specialties[0].id").value(21))
                .andExpect(jsonPath("$.data.jobs[1].specialtyCategories[0].specialties[0].name").value("디자인"));
    }

    @Test
    @DisplayName("OPEN 의뢰가 없으면 jobs 빈 배열을 응답하고 연관 데이터는 조회하지 않는다")
    void returnsEmptyJobs() throws Exception {
        givenOwner();
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.OPEN))
                .thenReturn(List.of());

        getOpenJobs("OPEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobs").isArray())
                .andExpect(jsonPath("$.data.jobs").isEmpty());

        verifyNoInteractions(jobSpecialtyRepository, jobApplicationRepository,
                specialtyRepository, specialtyCategoryRepository);
    }

    @Test
    @DisplayName("특기가 없는 OPEN 의뢰는 specialtyCategories 빈 배열을 응답한다")
    void returnsJobWithoutSpecialties() throws Exception {
        givenOwner();
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.OPEN))
                .thenReturn(List.of(job(42L, "웹사이트 제작")));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of());
        when(jobApplicationRepository.findByJobIdInAndStatus(List.of(42L), JobApplicationStatus.PENDING))
                .thenReturn(List.of());

        getOpenJobs("OPEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs[0].jobId").value(42))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories").isArray())
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories").isEmpty())
                .andExpect(jsonPath("$.data.jobs[0].applicantCount").value(0));

        verifyNoInteractions(specialtyRepository, specialtyCategoryRepository);
    }

    @Test
    @DisplayName("의뢰의 특기 참조가 누락되면 공통 500 응답을 반환한다")
    void failsWhenJobSpecialtyIsMissing() throws Exception {
        givenOwner();
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.OPEN))
                .thenReturn(List.of(job(42L, "웹사이트 제작")));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L)))
                .thenReturn(List.of(JobSpecialty.create(42L, 99L)));
        when(specialtyRepository.findAllById(any())).thenReturn(List.of());

        getOpenJobs("OPEN")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Specialty not found: 99"));
    }

    @Test
    @DisplayName("특기의 대분류 참조가 누락되면 공통 500 응답을 반환한다")
    void failsWhenSpecialtyCategoryIsMissing() throws Exception {
        givenOwner();
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.OPEN))
                .thenReturn(List.of(job(42L, "웹사이트 제작")));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L)))
                .thenReturn(List.of(JobSpecialty.create(42L, 11L)));
        when(specialtyRepository.findAllById(any()))
                .thenReturn(List.of(specialty(11L, 99L, "백엔드")));
        when(specialtyCategoryRepository.findAllById(any())).thenReturn(List.of());

        getOpenJobs("OPEN")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Specialty category not found: 99"));
    }

    @Test
    @DisplayName("사업주 프로필이 없으면 403을 응답하고 의뢰를 조회하지 않는다")
    void rejectsUserWithoutOwnerProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(ownerUser()));
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        getOpenJobs("OPEN")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OWNER_403"));

        verifyNoInteractions(jobRepository, jobSpecialtyRepository, jobApplicationRepository);
    }

    @Test
    @DisplayName("유효한 사용자를 찾지 못하면 401을 응답하고 의뢰를 조회하지 않는다")
    void rejectsUnknownOrLockedUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        getOpenJobs("OPEN")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(ownerRepository, jobRepository, jobSpecialtyRepository, jobApplicationRepository);
    }

    @Test
    @DisplayName("지원하지 않는 상태값이면 400을 응답하고 사용자 조회를 시작하지 않는다")
    void rejectsUnsupportedStatus() throws Exception {
        getOpenJobs("CLOSED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, ownerRepository, jobRepository);
    }

    private ResultActions getOpenJobs(String status) throws Exception {
        MockHttpServletRequestBuilder request = get("/me/jobs")
                .param("status", status)
                .principal(authentication);
        return mockMvc.perform(request)
                .andDo(result -> log.info("GET /me/jobs?status={} 응답: HTTP {}, {}",
                        status,
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString()));
    }

    private void givenOwner() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(ownerUser()));
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(Owner.builder().id(5L).build()));
    }

    private User ownerUser() {
        return User.builder().id(USER_ID).username(USERNAME).role(UserRole.OWNER).isLock(false).build();
    }

    private Job job(Long id, String title) {
        return Job.builder()
                .id(id)
                .ownerProfileId(5L)
                .title(title)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .revisionCount(2)
                .status(JobStatus.OPEN)
                .build();
    }

    private JobApplication application(Long jobId) {
        return JobApplication.builder()
                .jobId(jobId)
                .status(JobApplicationStatus.PENDING)
                .build();
    }

    private Specialty specialty(Long id, Long categoryId, String name) {
        return Specialty.builder().id(id).specialtyCategoryId(categoryId).name(name).build();
    }

    private SpecialtyCategory category(Long id, String name) {
        return SpecialtyCategory.builder().id(id).name(name).build();
    }
}
