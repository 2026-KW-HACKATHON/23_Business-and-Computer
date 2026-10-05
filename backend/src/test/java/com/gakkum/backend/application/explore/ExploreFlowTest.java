package com.gakkum.backend.application.explore;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.explore.controller.ExploreController;
import com.gakkum.backend.application.explore.dto.ExploreCursor;
import com.gakkum.backend.application.explore.dto.ExploreItemType;
import com.gakkum.backend.application.explore.dto.ExploreSort;
import com.gakkum.backend.application.explore.dto.ExploreType;
import com.gakkum.backend.application.explore.facade.ExploreFacade;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("탐색 전체 흐름 (GET /explore)")
class ExploreFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 28, 10, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 9, 29, 10, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 9, 30, 10, 0);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProposalRepository proposalRepository = mock(ProposalRepository.class);
    private final ProposalSpecialtyRepository proposalSpecialtyRepository = mock(ProposalSpecialtyRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final SpecialtyCategoryRepository specialtyCategoryRepository = mock(SpecialtyCategoryRepository.class);

    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ExploreFacade facade = new ExploreFacade(
                new UserService(userRepository, mock(JwtService.class)),
                new ProposalService(proposalRepository, proposalSpecialtyRepository),
                new JobService(jobRepository, jobSpecialtyRepository, mock(JobApplicationRepository.class),
                        jobSubmissionRepository, Clock.systemUTC()),
                new OwnerService(ownerRepository),
                new SpecialtyCategoryService(specialtyCategoryRepository, specialtyRepository),
                mock(BusinessCategoryService.class));

        mockMvc = MockMvcBuilders.standaloneSetup(new ExploreController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("제안과 취소되지 않은 의뢰를 최신순으로 섞어 카드마다 매장·분류·진행 단계를 채우고 다음 커서를 응답한다")
    void returnsMixedCardsThroughAllLayers() throws Exception {
        givenActiveUser();
        when(proposalRepository.findByDemoSessionIdAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(any(), any(), eq(Limit.of(3))))
                .thenReturn(List.of(proposal(31L, T2, 4, 50L), proposal(30L, T1, 0, 50L)));
        when(jobRepository.findByDemoSessionIdAndStatusNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                any(), eq(JobStatus.CANCELLED), any(), eq(Limit.of(3))))
                .thenReturn(List.of(job(42L, T3, JobStatus.MATCHED, 60L), job(41L, T1, JobStatus.OPEN, 60L)));
        when(proposalSpecialtyRepository.findByProposalIdIn(List.of(31L, 30L))).thenReturn(List.of(
                ProposalSpecialty.create(31L, 12L), ProposalSpecialty.create(31L, 11L),
                ProposalSpecialty.create(31L, 21L), ProposalSpecialty.create(30L, 11L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L, 41L))).thenReturn(List.of(
                JobSpecialty.create(42L, 21L), JobSpecialty.create(41L, 11L)));
        when(jobSubmissionRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of(JobSubmission.builder()
                .jobId(42L).submissionType(JobSubmissionType.DRAFT).revisionNumber(0)
                .reviewStatus(JobSubmissionReviewStatus.REVISION_REQUESTED).build()));
        when(ownerRepository.findAllById(any())).thenReturn(List.of(
                Owner.builder().id(50L).storeName("가꿈 분식").build(),
                Owner.builder().id(60L).storeName("가꿈 카페").build()));
        givenSpecialties();

        explore(get("/explore").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].type").value("JOB"))
                .andExpect(jsonPath("$.data.items[0].jobId").value(42))
                .andExpect(jsonPath("$.data.items[0].storeName").value("가꿈 카페"))
                .andExpect(jsonPath("$.data.items[0].title").value("의뢰 42"))
                .andExpect(jsonPath("$.data.items[0].status").value("MATCHED"))
                .andExpect(jsonPath("$.data.items[0].progressStage").value("REVISION"))
                .andExpect(jsonPath("$.data.items[0].draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.items[0].finalDeadline").value("2026-10-20"))
                .andExpect(jsonPath("$.data.items[0].specialtyCategories[0].name").value("디자인"))
                .andExpect(jsonPath("$.data.items[1].type").value("PROPOSAL"))
                .andExpect(jsonPath("$.data.items[1].proposalId").value(31))
                .andExpect(jsonPath("$.data.items[1].title").value("제안 31"))
                .andExpect(jsonPath("$.data.items[1].storeName").value("가꿈 분식"))
                .andExpect(jsonPath("$.data.items[1].likeCount").value(4))
                .andExpect(jsonPath("$.data.items[1].specialtyCategories.length()").value(2))
                .andExpect(jsonPath("$.data.items[1].specialtyCategories[0].id").value(1))
                .andExpect(jsonPath("$.data.items[1].specialtyCategories[0].specialties[0].id").value(11))
                .andExpect(jsonPath("$.data.items[1].specialtyCategories[0].specialties[1].id").value(12))
                .andExpect(jsonPath("$.data.items[1].specialtyCategories[1].id").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isString());
    }

    @Test
    @DisplayName("다음 커서로 요청하면 커서 카드 바로 뒤부터 두 종류를 읽도록 저장소에 경계를 넘긴다")
    void readsAfterCursor() throws Exception {
        givenActiveUser();
        String cursor = ExploreCursor.of(ExploreSort.LATEST, ExploreType.ALL, null,
                ExploreItemType.PROPOSAL, null, T2, 31L).encode();

        explore(get("/explore").param("size", "2").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));

        // 같은 시각 제안은 커서 ID 앞만, 같은 시각 의뢰는 제안 뒤라 모두 읽는다
        verify(proposalRepository).findByDemoSessionIdAndCreatedAtAndIdLessThanOrderByIdDesc(null, T2, 31L, Limit.of(3));
        verify(proposalRepository).findByDemoSessionIdAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(null, T2, Limit.of(3));
        verify(jobRepository).findByDemoSessionIdAndStatusNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, JobStatus.CANCELLED, T2, Long.MAX_VALUE, Limit.of(3));
        verifyNoInteractions(ownerRepository, specialtyRepository);
    }

    @Test
    @DisplayName("대분류 좋아요순 제안 탐색은 분류 쿼리 하나로 읽고 의뢰는 조회하지 않는다")
    void usesCategoryQueryForProposalLikes() throws Exception {
        givenActiveUser();

        explore(get("/explore").param("specialtyCategoryId", "3").param("type", "PROPOSAL").param("sort", "LIKES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(proposalRepository).findExploreByLikesInCategory(
                any(), eq(3L), eq(Integer.MAX_VALUE), any(), eq(Long.MAX_VALUE), eq(Limit.of(21)));
        verifyNoInteractions(jobRepository);
    }

    @Test
    @DisplayName("다른 필터로 만든 커서는 COMMON_400으로 거부하고 사용자·저장소를 조회하지 않는다")
    void rejectsCursorFromOtherFilter() throws Exception {
        String cursor = ExploreCursor.of(ExploreSort.LATEST, ExploreType.ALL, 3L,
                ExploreItemType.JOB, null, T2, 42L).encode();

        explore(get("/explore").param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, proposalRepository, jobRepository);
    }

    @Test
    @DisplayName("잠긴 사용자는 COMMON_401로 거부하고 목록을 조회하지 않는다")
    void rejectsLockedUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        explore(get("/explore"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(proposalRepository, jobRepository);
    }

    private ResultActions explore(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.principal(authentication));
    }

    private void givenActiveUser() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(USER_ID).username(USERNAME).role(UserRole.STUDENT).isLock(false).build()));
    }

    private void givenSpecialties() {
        when(specialtyRepository.findAllById(any())).thenReturn(List.of(
                Specialty.builder().id(11L).specialtyCategoryId(1L).name("로고 디자인").build(),
                Specialty.builder().id(12L).specialtyCategoryId(1L).name("포스터 디자인").build(),
                Specialty.builder().id(21L).specialtyCategoryId(2L).name("디자인").build()));
        when(specialtyCategoryRepository.findAllById(any())).thenReturn(List.of(
                SpecialtyCategory.builder().id(1L).name("디자인 기획").build(),
                SpecialtyCategory.builder().id(2L).name("디자인").build()));
    }

    private Proposal proposal(Long id, LocalDateTime createdAt, int likeCount, Long ownerProfileId) {
        return Proposal.builder()
                .id(id)
                .ownerProfileId(ownerProfileId)
                .title("제안 " + id)
                .likeCount(likeCount)
                .createdAt(createdAt)
                .build();
    }

    private Job job(Long id, LocalDateTime createdAt, JobStatus status, Long ownerProfileId) {
        return Job.builder()
                .id(id)
                .ownerProfileId(ownerProfileId)
                .title("의뢰 " + id)
                .status(status)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .createdAt(createdAt)
                .build();
    }
}
