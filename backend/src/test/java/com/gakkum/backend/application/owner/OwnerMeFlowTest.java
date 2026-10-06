package com.gakkum.backend.application.owner;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.owner.controller.OwnerController;
import com.gakkum.backend.application.owner.facade.OwnerFacade;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
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
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalLikeRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("사장님 내 정보 조회 전체 흐름 (GET /owners/me)")
class OwnerMeFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final Long OWNER_PROFILE_ID = 5L;
    private static final String URL = "/owners/me";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final ProposalRepository proposalRepository = mock(ProposalRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtService jwtService = mock(JwtService.class);
        OwnerFacade facade = new OwnerFacade(
                new UserService(userRepository, jwtService),
                new OwnerService(ownerRepository),
                mock(BusinessCategoryService.class),
                jwtService,
                new JobService(jobRepository, mock(JobSpecialtyRepository.class), jobApplicationRepository,
                        mock(JobSubmissionRepository.class), Clock.systemUTC()),
                new ProposalService(proposalRepository, mock(ProposalSpecialtyRepository.class),
                        mock(ProposalLikeRepository.class)));
        mockMvc = MockMvcBuilders.standaloneSetup(new OwnerController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("사장님 본인의 프로필과 활동 개수 9개 필드를 반환하고 이름은 대표자명이 아닌 가입자 이름이다")
    void returnsOwnInformation() throws Exception {
        givenOwner(UserRole.OWNER, "https://cdn.gakkum.test/owner.png", "서울시 노원구 광운로 20");
        when(jobRepository.countByOwnerProfileIdAndStatusNot(OWNER_PROFILE_ID, JobStatus.CANCELLED)).thenReturn(7L);
        when(proposalRepository.countByOwnerProfileIdAndStatusNot(OWNER_PROFILE_ID, ProposalStatus.CANCELLED))
                .thenReturn(4L);
        when(jobRepository.countByOwnerProfileIdAndStatus(OWNER_PROFILE_ID, JobStatus.MATCHED)).thenReturn(2L);
        when(jobRepository.countByOwnerProfileIdAndStatus(OWNER_PROFILE_ID, JobStatus.CLOSED)).thenReturn(3L);

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(9))
                .andExpect(jsonPath("$.data.ownerProfileId").value(5))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.gakkum.test/owner.png"))
                .andExpect(jsonPath("$.data.name").value("김가입"))
                .andExpect(jsonPath("$.data.storeName").value("가꿈 베이커리"))
                .andExpect(jsonPath("$.data.storeAddress").value("서울시 노원구 광운로 20"))
                .andExpect(jsonPath("$.data.sentJobCount").value(7))
                .andExpect(jsonPath("$.data.receivedProposalCount").value(4))
                .andExpect(jsonPath("$.data.inProgressJobCount").value(2))
                .andExpect(jsonPath("$.data.completedJobCount").value(3));
    }

    @Test
    @DisplayName("활동 개수는 본인 사장님 프로필 ID로만 집계하고 받은 제안은 지원서가 아닌 제안 기준이다")
    void countsByOwnOwnerProfileId() throws Exception {
        givenOwner(UserRole.OWNER, null, null);

        perform().andExpect(status().isOk());

        verify(jobRepository).countByOwnerProfileIdAndStatusNot(OWNER_PROFILE_ID, JobStatus.CANCELLED);
        verify(jobRepository).countByOwnerProfileIdAndStatus(OWNER_PROFILE_ID, JobStatus.MATCHED);
        verify(jobRepository).countByOwnerProfileIdAndStatus(OWNER_PROFILE_ID, JobStatus.CLOSED);
        verify(proposalRepository).countByOwnerProfileIdAndStatusNot(OWNER_PROFILE_ID, ProposalStatus.CANCELLED);
        verify(ownerRepository, never()).findById(OWNER_PROFILE_ID);
        verifyNoInteractions(jobApplicationRepository);
    }

    @Test
    @DisplayName("활동이 없는 사장님은 개수 0을 받고 사진 URL과 매장 주소는 저장된 null 그대로 받는다")
    void returnsEmptyDefaults() throws Exception {
        givenOwner(UserRole.OWNER, null, null);

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(9))
                .andExpect(jsonPath("$.data.profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.storeAddress").value(nullValue()))
                .andExpect(jsonPath("$.data.sentJobCount").value(0))
                .andExpect(jsonPath("$.data.receivedProposalCount").value(0))
                .andExpect(jsonPath("$.data.inProgressJobCount").value(0))
                .andExpect(jsonPath("$.data.completedJobCount").value(0));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STUDENT", "PENDING"})
    @DisplayName("학생이나 가입 미완료 사용자가 조회하면 403 OWNER_403_ME를 반환하고 사장님 데이터를 조회하지 않는다")
    void rejectsNonOwner(UserRole role) throws Exception {
        givenOwner(role, null, null);

        perform()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OWNER_403_ME"));
        verifyNoInteractions(ownerRepository, jobRepository, proposalRepository);
    }

    @Test
    @DisplayName("잠겼거나 존재하지 않는 사용자는 401 COMMON_401로 거부한다")
    void rejectsInactiveUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        perform()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(ownerRepository, jobRepository, proposalRepository);
    }

    @Test
    @DisplayName("사장님 역할인데 사장님 프로필이 없으면 500 COMMON_500을 반환한다")
    void rejectsOwnerWithoutProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).name("김가입").build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.empty());

        perform()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
        verifyNoInteractions(jobRepository, proposalRepository);
    }

    private ResultActions perform() throws Exception {
        return mockMvc.perform(get(URL).principal(authentication));
    }

    private void givenOwner(UserRole role, String profileImageUrl, String storeAddress) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(role).name("김가입").build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.of(Owner.builder()
                .id(OWNER_PROFILE_ID)
                .userId(OWNER_USER_ID)
                .representativeName("김대표")
                .storeName("가꿈 베이커리")
                .storeAddress(storeAddress)
                .profileImageUrl(profileImageUrl)
                .build()));
    }
}
