package com.gakkum.backend.application.job;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.repository.StudentSpecialtyRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("의뢰 상세 조회 전체 흐름 (GET /jobs/{jobId})")
class JobDetailFlowTest {

    private static final String USERNAME = "KAKAO_12345";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final SpecialtyCategoryRepository specialtyCategoryRepository = mock(SpecialtyCategoryRepository.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, mock(JwtService.class));
        SpecialtyService specialtyService = new SpecialtyService(specialtyRepository,
                mock(StudentSpecialtyRepository.class));
        JobService jobService = new JobService(jobRepository, jobSpecialtyRepository,
                mock(JobApplicationRepository.class), mock(JobSubmissionRepository.class), specialtyService);
        SpecialtyCategoryService specialtyCategoryService =
                new SpecialtyCategoryService(specialtyCategoryRepository, specialtyRepository);
        JobFacade facade = new JobFacade(userService, mock(OwnerService.class), jobService,
                specialtyCategoryService, mock(StudentService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    @DisplayName("작성자가 아닌 학생도 모든 상태의 의뢰 상세 필드를 조회한다")
    void returnsJobDetailForEveryStatus(JobStatus jobStatus) throws Exception {
        givenActiveStudent();
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job(jobStatus)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of(
                JobSpecialty.create(42L, 21L),
                JobSpecialty.create(42L, 12L),
                JobSpecialty.create(42L, 11L)));
        when(specialtyRepository.findAllById(any())).thenReturn(List.of(
                specialty(21L, 2L, "웹 디자인"),
                specialty(12L, 1L, "프론트엔드"),
                specialty(11L, 1L, "백엔드")));
        when(specialtyCategoryRepository.findAllById(any())).thenReturn(List.of(
                category(2L, "디자인"), category(1L, "개발")));

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.jobId").doesNotExist())
                .andExpect(jsonPath("$.data.ownerProfileId").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.title").value("가게 홍보 웹사이트 제작"))
                .andExpect(jsonPath("$.data.description").value("메뉴와 위치를 소개하는 웹사이트가 필요합니다."))
                .andExpect(jsonPath("$.data.budget").value(300000))
                .andExpect(jsonPath("$.data.specialtyCategories.length()").value(2))
                .andExpect(jsonPath("$.data.specialtyCategories[0].id").value(1))
                .andExpect(jsonPath("$.data.specialtyCategories[0].name").value("개발"))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[0].id").value(11))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[0].name").value("백엔드"))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[1].id").value(12))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[1].name").value("프론트엔드"))
                .andExpect(jsonPath("$.data.specialtyCategories[1].id").value(2))
                .andExpect(jsonPath("$.data.specialtyCategories[1].name").value("디자인"))
                .andExpect(jsonPath("$.data.specialtyCategories[1].specialties[0].id").value(21))
                .andExpect(jsonPath("$.data.specialtyCategories[1].specialties[0].name").value("웹 디자인"))
                .andExpect(jsonPath("$.data.draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.finalDeadline").value("2026-10-20"))
                .andExpect(jsonPath("$.data.revisionCount").value(1));
    }

    @Test
    @DisplayName("특기가 없는 의뢰는 specialtyCategories 빈 배열을 반환한다")
    void returnsEmptySpecialtyCategories() throws Exception {
        givenActiveStudent();
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job(JobStatus.OPEN)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of());

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.specialtyCategories").isArray())
                .andExpect(jsonPath("$.data.specialtyCategories").isEmpty());
        verifyNoInteractions(specialtyRepository, specialtyCategoryRepository);
    }

    @Test
    @DisplayName("존재하지 않는 의뢰는 전용 404 응답을 반환한다")
    void returnsNotFound() throws Exception {
        givenActiveStudent();
        when(jobRepository.findById(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(jobSpecialtyRepository, specialtyRepository, specialtyCategoryRepository);
    }

    @Test
    @DisplayName("0 이하 또는 숫자가 아닌 의뢰 ID는 공통 400 응답을 반환한다")
    void rejectsInvalidJobId() throws Exception {
        for (String jobId : List.of("0", "-1", "abc")) {
            mockMvc.perform(get("/jobs/{jobId}", jobId).principal(authentication))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        verifyNoInteractions(userRepository, jobRepository, jobSpecialtyRepository);
    }

    @Test
    @DisplayName("활성 사용자를 찾지 못하면 401을 반환하고 의뢰를 조회하지 않는다")
    void rejectsInactiveUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        mockMvc.perform(get("/jobs/42").principal(authentication))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(jobRepository, jobSpecialtyRepository);
    }

    private void givenActiveStudent() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false))
                .thenReturn(Optional.of(User.builder().username(USERNAME).role(UserRole.STUDENT).isLock(false).build()));
    }

    private Job job(JobStatus status) {
        return Job.builder()
                .id(42L)
                .ownerProfileId(999L)
                .title("가게 홍보 웹사이트 제작")
                .description("메뉴와 위치를 소개하는 웹사이트가 필요합니다.")
                .budget(300000L)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .revisionCount(1)
                .status(status)
                .build();
    }

    private Specialty specialty(Long id, Long categoryId, String name) {
        return Specialty.builder().id(id).specialtyCategoryId(categoryId).name(name).build();
    }

    private SpecialtyCategory category(Long id, String name) {
        return SpecialtyCategory.builder().id(id).name(name).build();
    }
}
