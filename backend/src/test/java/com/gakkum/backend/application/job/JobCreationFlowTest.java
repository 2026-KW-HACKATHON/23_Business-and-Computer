package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobCreationController;
import com.gakkum.backend.application.job.facade.JobCreationFacade;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.repository.StudentSpecialtyRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("의뢰 생성 전체 흐름 (POST /jobs)")
class JobCreationFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";

    private static final String REQUEST_BODY = """
            {
              "specialtyIds": [1, 2, 3],
              "title": "의뢰 제목",
              "description": "맡기고 싶은 일",
              "budget": 500000,
              "draftDeadline": "2999-01-01",
              "finalDeadline": "2999-01-15",
              "revisionCount": 1
            }
            """;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final StudentSpecialtyRepository studentSpecialtyRepository = mock(StudentSpecialtyRepository.class);
    private final JwtService jwtService = mock(JwtService.class);

    private final User ownerUser = User.builder()
            .id(USER_ID)
            .username(USERNAME)
            .isLock(false)
            .role(UserRole.OWNER)
            .build();
    private final Owner ownerProfile = Owner.builder()
            .id(5L)
            .userId(USER_ID)
            .businessNumber("1234567890")
            .storeName("치킨플러스")
            .categoryId(2L)
            .storeImageUrls(List.of())
            .build();
    private final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            USERNAME,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_OWNER")));

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, jwtService);
        OwnerService ownerService = new OwnerService(ownerRepository);
        SpecialtyService specialtyService = new SpecialtyService(specialtyRepository, studentSpecialtyRepository);
        JobService jobService = new JobService(jobRepository, jobSpecialtyRepository, specialtyService);
        JobCreationFacade facade = new JobCreationFacade(userService, ownerService, jobService);
        JobCreationController controller = new JobCreationController(facade);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("정상 요청이면 의뢰와 특기가 저장되고 success: true, data: null이 반환된다")
    void createsJobThroughControllerFacadeAndDomainServices() throws Exception {
        givenOwner();
        when(specialtyRepository.countByIdIn(List.of(1L, 2L, 3L))).thenReturn(3L);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            return Job.builder()
                    .id(100L)
                    .ownerProfileId(job.getOwnerProfileId())
                    .title(job.getTitle())
                    .description(job.getDescription())
                    .budget(job.getBudget())
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .revisionCount(job.getRevisionCount())
                    .status(job.getStatus())
                    .build();
        });

        createJob(REQUEST_BODY)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.error").isEmpty());

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        Job job = jobCaptor.getValue();
        assertThat(job.getOwnerProfileId()).isEqualTo(ownerProfile.getId());
        assertThat(job.getTitle()).isEqualTo("의뢰 제목");
        assertThat(job.getDescription()).isEqualTo("맡기고 싶은 일");
        assertThat(job.getBudget()).isEqualTo(500000L);
        assertThat(job.getRevisionCount()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(JobStatus.OPEN);

        ArgumentCaptor<List<JobSpecialty>> specialtiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(jobSpecialtyRepository).saveAll(specialtiesCaptor.capture());
        assertThat(specialtiesCaptor.getValue())
                .extracting(JobSpecialty::getJobId, JobSpecialty::getSpecialtyId)
                .containsExactly(
                        Tuple.tuple(100L, 1L),
                        Tuple.tuple(100L, 2L),
                        Tuple.tuple(100L, 3L));
    }

    @Test
    @DisplayName("존재하지 않거나 잠긴 사용자는 401을 반환하고 아무것도 저장하지 않는다")
    void rejectsUnknownOrLockedUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        createJob(REQUEST_BODY)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(ownerRepository, jobRepository, jobSpecialtyRepository, specialtyRepository);
    }

    @Test
    @DisplayName("사장님 프로필이 없는 사용자는 403을 반환하고 아무것도 저장하지 않는다")
    void rejectsUserWithoutOwnerProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(ownerUser));
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        createJob(REQUEST_BODY)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("OWNER_403"));

        verifyNoInteractions(jobRepository, jobSpecialtyRepository, specialtyRepository);
    }

    @Test
    @DisplayName("존재하지 않는 특기가 포함되면 400을 반환하고 저장하지 않는다")
    void rejectsUnknownSpecialty() throws Exception {
        givenOwner();
        when(specialtyRepository.countByIdIn(List.of(1L, 2L, 3L))).thenReturn(2L);

        createJob(REQUEST_BODY)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("SPECIALTY_400"));

        verify(jobRepository, never()).save(any());
        verifyNoInteractions(jobSpecialtyRepository);
    }

    @Test
    @DisplayName("필수값이 없으면 400을 반환하고 인증 사용자를 조회하지 않는다")
    void rejectsMissingRequiredFields() throws Exception {
        createJob("{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, ownerRepository, jobRepository, jobSpecialtyRepository);
    }

    @Test
    @DisplayName("선지급 마감일이 최종 마감일보다 늦으면 400을 반환한다")
    void rejectsDraftDeadlineAfterFinalDeadline() throws Exception {
        createJob(REQUEST_BODY
                .replace("\"draftDeadline\": \"2999-01-01\"", "\"draftDeadline\": \"2999-01-20\""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, ownerRepository, jobRepository, jobSpecialtyRepository);
    }

    @Test
    @DisplayName("수정 횟수가 음수이면 400을 반환한다")
    void rejectsNegativeRevisionCount() throws Exception {
        createJob(REQUEST_BODY.replace("\"revisionCount\": 1", "\"revisionCount\": -1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, ownerRepository, jobRepository, jobSpecialtyRepository);
    }

    private ResultActions createJob(String body) throws Exception {
        return mockMvc.perform(post("/jobs")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void givenOwner() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(ownerUser));
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(ownerProfile));
    }
}
