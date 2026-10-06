package com.gakkum.backend.application.explore;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
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
import com.gakkum.backend.domain.proposal.entity.ProposalLike;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalLikeRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("탐색 전체 흐름 (GET /explore)")
class ExploreFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final long STUDENT_PROFILE_ID = 77L;
    private static final long AUTHOR_PROFILE_ID = 70L;
    private static final String AUTHOR_USER_ID = "01K58M6PJV8VAJMXHBHJ2PAUTH";
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 28, 10, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 9, 29, 10, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 9, 30, 10, 0);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProposalRepository proposalRepository = mock(ProposalRepository.class);
    private final ProposalSpecialtyRepository proposalSpecialtyRepository = mock(ProposalSpecialtyRepository.class);
    private final ProposalLikeRepository proposalLikeRepository = mock(ProposalLikeRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
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
                new ProposalService(proposalRepository, proposalSpecialtyRepository, proposalLikeRepository),
                new JobService(jobRepository, jobSpecialtyRepository, jobApplicationRepository,
                        jobSubmissionRepository, Clock.systemUTC()),
                new OwnerService(ownerRepository),
                new SpecialtyCategoryService(specialtyCategoryRepository, specialtyRepository),
                mock(BusinessCategoryService.class),
                new StudentService(studentRepository));

        mockMvc = MockMvcBuilders.standaloneSetup(new ExploreController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("제안과 취소되지 않은 의뢰를 최신순으로 섞어 카드마다 매장·분류·진행 단계를 채우고 다음 커서를 응답한다")
    void returnsMixedCardsThroughAllLayers() throws Exception {
        givenActiveUser();
        when(proposalRepository.findByDemoSessionIdAndStatusNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(any(), eq(ProposalStatus.CANCELLED), any(), eq(Limit.of(3))))
                .thenReturn(List.of(proposal(31L, T2, 4, 50L), proposal(30L, T1, 0, 50L, 71L)));
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
        when(studentRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(Student.builder().id(STUDENT_PROFILE_ID).userId(USER_ID).build()));
        when(jobApplicationRepository.findByStudentProfileIdAndJobIdIn(STUDENT_PROFILE_ID, List.of(42L)))
                .thenReturn(List.of(application(42L, JobApplicationStatus.PENDING)));
        // 다음 페이지로 밀린 제안 30의 작성자(71)와 공감 기록은 조회하지 않는다
        givenAuthor("김학생");
        when(proposalLikeRepository.findByStudentProfileIdAndProposalIdIn(STUDENT_PROFILE_ID, List.of(31L)))
                .thenReturn(List.of(ProposalLike.create(31L, STUDENT_PROFILE_ID)));

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
                .andExpect(jsonPath("$.data.items[0].budget").value(300000))
                .andExpect(jsonPath("$.data.items[0].applied").value("PENDING"))
                .andExpect(jsonPath("$.data.items[1]", not(hasKey("applied"))))
                .andExpect(jsonPath("$.data.items[1]", not(hasKey("budget"))))
                .andExpect(jsonPath("$.data.items[0].specialtyCategories[0].name").value("디자인"))
                .andExpect(jsonPath("$.data.items[1].type").value("PROPOSAL"))
                .andExpect(jsonPath("$.data.items[1].proposalId").value(31))
                .andExpect(jsonPath("$.data.items[1].title").value("제안 31"))
                .andExpect(jsonPath("$.data.items[1].storeName").value("가꿈 분식"))
                .andExpect(jsonPath("$.data.items[1].likeCount").value(4))
                .andExpect(jsonPath("$.data.items[1].studentName").value("김학생"))
                .andExpect(jsonPath("$.data.items[1].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[1].proposedSolution").value("해결 방안 31"))
                .andExpect(jsonPath("$.data.items[1].likedByMe").value(true))
                .andExpect(jsonPath("$.data.items[0]", not(hasKey("likedByMe"))))
                .andExpect(jsonPath("$.data.items[0]", not(hasKey("studentName"))))
                .andExpect(jsonPath("$.data.items[1].specialtyCategories.length()").value(2))
                .andExpect(jsonPath("$.data.items[1].specialtyCategories[0].id").value(1))
                .andExpect(jsonPath("$.data.items[1].specialtyCategories[0].specialties[0].id").value(11))
                .andExpect(jsonPath("$.data.items[1].specialtyCategories[0].specialties[1].id").value(12))
                .andExpect(jsonPath("$.data.items[1].specialtyCategories[1].id").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isString());

        verify(studentRepository).findAllById(List.of(AUTHOR_PROFILE_ID));
        verify(studentRepository).findByUserId(USER_ID);
    }

    @Test
    @DisplayName("학생의 제안 탐색은 본인 공감 기록이 있는 제안만 true, 다른 학생만 공감했거나 아무도 공감하지 않은 제안은 false로 응답한다")
    void returnsLikedByMeOnlyForOwnLikes() throws Exception {
        givenActiveUser();
        givenProposals(proposal(33L, T3, 2, 50L), proposal(32L, T2, 5, 50L), proposal(31L, T1, 0, 50L));
        givenAuthor("김학생");
        when(studentRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(Student.builder().id(STUDENT_PROFILE_ID).userId(USER_ID).build()));
        // 제안 32에는 다른 학생의 공감 기록만 있어 본인 조회 결과에 없다
        when(proposalLikeRepository.findByStudentProfileIdAndProposalIdIn(STUDENT_PROFILE_ID, List.of(33L, 32L, 31L)))
                .thenReturn(List.of(ProposalLike.create(33L, STUDENT_PROFILE_ID)));

        explore(get("/explore").param("type", "PROPOSAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].proposalId").value(33))
                .andExpect(jsonPath("$.data.items[0].likedByMe").value(true))
                .andExpect(jsonPath("$.data.items[1].likeCount").value(5))
                .andExpect(jsonPath("$.data.items[1].likedByMe").value(false))
                .andExpect(jsonPath("$.data.items[2].likeCount").value(0))
                .andExpect(jsonPath("$.data.items[2].likedByMe").value(false))
                .andExpect(jsonPath("$.data.items[2].studentName").value("김학생"));

        // 세 제안의 작성자가 같아 학생 프로필과 사용자를 한 번씩만 조회한다
        verify(studentRepository).findAllById(List.of(AUTHOR_PROFILE_ID));
        verify(userRepository).findAllById(List.of(AUTHOR_USER_ID));
        verifyNoInteractions(jobApplicationRepository);
    }

    @Test
    @DisplayName("학생 프로필이 없는 학생도 제안 탐색에 성공하고 공감 기록을 조회하지 않은 채 likedByMe를 false로 응답한다")
    void returnsNotLikedForStudentWithoutProfile() throws Exception {
        givenActiveUser();
        givenProposals(proposal(31L, T2, 4, 50L));
        givenAuthor("김학생");
        when(studentRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        explore(get("/explore").param("type", "PROPOSAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0]", hasKey("likedByMe")))
                .andExpect(jsonPath("$.data.items[0].likedByMe").value(false));

        verifyNoInteractions(proposalLikeRepository);
    }

    @Test
    @DisplayName("학생이 아닌 사용자의 제안 카드는 likedByMe를 false로 내리고 학생 이름·상태·해결 방안은 그대로 담으며 공감 기록을 조회하지 않는다")
    void returnsLikedByMeFalseForNonStudent() throws Exception {
        givenActiveUser(UserRole.OWNER);
        givenProposals(proposal(31L, T2, 4, 50L));
        givenAuthor("김학생");

        explore(get("/explore").param("type", "PROPOSAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0]", hasKey("likedByMe")))
                .andExpect(jsonPath("$.data.items[0].likedByMe").value(false))
                .andExpect(jsonPath("$.data.items[0].studentName").value("김학생"))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].proposedSolution").value("해결 방안 31"));

        verify(studentRepository, never()).findByUserId(any());
        verifyNoInteractions(proposalLikeRepository);
    }

    @Test
    @DisplayName("제안 작성자의 학생 프로필을 찾지 못하면 COMMON_500으로 응답한다")
    void failsWhenProposalAuthorIsMissing() throws Exception {
        givenActiveUser();
        givenProposals(proposal(31L, T2, 4, 50L));

        explore(get("/explore").param("type", "PROPOSAL"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("학생의 의뢰 탐색은 본인 지원서의 대기·선정·거절 상태를 문자열 그대로 응답하고, 본인 지원서가 없는 의뢰는 applied 키를 내리지 않는다")
    void returnsOwnApplicationStatusForStudent() throws Exception {
        givenActiveUser();
        givenJobs(job(44L, T3, JobStatus.OPEN, 60L), job(43L, T3, JobStatus.OPEN, 60L),
                job(42L, T3, JobStatus.OPEN, 60L), job(41L, T3, JobStatus.OPEN, 60L));
        when(studentRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(Student.builder().id(STUDENT_PROFILE_ID).userId(USER_ID).build()));
        // 의뢰 41에는 다른 학생의 지원서만 있어 본인 조회 결과에 없다
        when(jobApplicationRepository.findByStudentProfileIdAndJobIdIn(STUDENT_PROFILE_ID, List.of(44L, 43L, 42L, 41L)))
                .thenReturn(List.of(application(44L, JobApplicationStatus.PENDING),
                        application(43L, JobApplicationStatus.ACCEPTED),
                        application(42L, JobApplicationStatus.REJECTED)));

        explore(get("/explore").param("type", "JOB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].applied").isString())
                .andExpect(jsonPath("$.data.items[0].applied").value("PENDING"))
                .andExpect(jsonPath("$.data.items[1].applied").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.items[2].applied").value("REJECTED"))
                // 공고 상태와 본인 지원 상태는 별개로 내린다
                .andExpect(jsonPath("$.data.items[2].status").value("OPEN"))
                .andExpect(jsonPath("$.data.items[3].jobId").value(41))
                .andExpect(jsonPath("$.data.items[3]", not(hasKey("applied"))))
                .andExpect(jsonPath("$.data.items[3].budget").value(300000));

        // 이번 페이지의 의뢰 지원서를 한 번에 조회한다
        verify(jobApplicationRepository).findByStudentProfileIdAndJobIdIn(STUDENT_PROFILE_ID, List.of(44L, 43L, 42L, 41L));

        // 제안 카드가 없는 페이지에서는 작성자와 공감 기록을 조회하지 않는다
        verify(studentRepository, never()).findAllById(any());
        verify(userRepository, never()).findAllById(any());
        verifyNoInteractions(proposalLikeRepository);
    }

    @Test
    @DisplayName("학생 프로필이 없는 학생도 탐색에 성공하고 지원서를 조회하지 않은 채 applied 키를 내리지 않는다")
    void omitsAppliedForStudentWithoutProfile() throws Exception {
        givenActiveUser();
        givenJobs(job(42L, T3, JobStatus.OPEN, 60L));
        when(studentRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        explore(get("/explore").param("type", "JOB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].jobId").value(42))
                .andExpect(jsonPath("$.data.items[0]", not(hasKey("applied"))));

        verifyNoInteractions(jobApplicationRepository);
    }

    @Test
    @DisplayName("학생이 아닌 사용자의 의뢰 카드는 예산만 담고 applied 키를 내리지 않으며 학생 프로필·지원서를 조회하지 않는다")
    void omitsAppliedForNonStudent() throws Exception {
        givenActiveUser(UserRole.OWNER);
        givenJobs(job(42L, T3, JobStatus.OPEN, 60L));

        explore(get("/explore").param("type", "JOB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].budget").value(300000))
                .andExpect(jsonPath("$.data.items[0]", not(hasKey("applied"))));

        verifyNoInteractions(studentRepository, jobApplicationRepository);
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
        verify(proposalRepository).findByDemoSessionIdAndStatusNotAndCreatedAtAndIdLessThanOrderByIdDesc(null, ProposalStatus.CANCELLED, T2, 31L, Limit.of(3));
        verify(proposalRepository).findByDemoSessionIdAndStatusNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(null, ProposalStatus.CANCELLED, T2, Limit.of(3));
        verify(jobRepository).findByDemoSessionIdAndStatusNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, JobStatus.CANCELLED, T2, Long.MAX_VALUE, Limit.of(3));
        verifyNoInteractions(ownerRepository, specialtyRepository, studentRepository, proposalLikeRepository);
    }

    @Test
    @DisplayName("대분류 좋아요순 제안 탐색은 분류 쿼리 하나로 읽고 의뢰는 조회하지 않는다")
    void usesCategoryQueryForProposalLikes() throws Exception {
        givenActiveUser();

        explore(get("/explore").param("specialtyCategoryId", "3").param("type", "PROPOSAL").param("sort", "LIKES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(proposalRepository).findExploreByLikesInCategory(
                any(), eq(ProposalStatus.CANCELLED), eq(3L), eq(Integer.MAX_VALUE), any(), eq(Long.MAX_VALUE), eq(Limit.of(21)));
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
        givenActiveUser(UserRole.STUDENT);
    }

    private void givenActiveUser(UserRole role) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(USER_ID).username(USERNAME).role(role).isLock(false).build()));
    }

    private void givenJobs(Job... jobs) {
        when(jobRepository.findByDemoSessionIdAndStatusNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                any(), eq(JobStatus.CANCELLED), any(), any())).thenReturn(List.of(jobs));
        when(ownerRepository.findAllById(any())).thenReturn(List.of(
                Owner.builder().id(60L).storeName("가꿈 카페").build()));
    }

    private void givenProposals(Proposal... proposals) {
        when(proposalRepository.findByDemoSessionIdAndStatusNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(any(), eq(ProposalStatus.CANCELLED), any(), any()))
                .thenReturn(List.of(proposals));
        when(ownerRepository.findAllById(any())).thenReturn(List.of(
                Owner.builder().id(50L).storeName("가꿈 분식").build()));
    }

    // 기본 작성자(학생 프로필 70)만 등록한다. 다른 작성자를 조회하면 일괄 조회가 COMMON_500으로 실패한다
    private void givenAuthor(String name) {
        when(studentRepository.findAllById(List.of(AUTHOR_PROFILE_ID))).thenReturn(List.of(
                Student.builder().id(AUTHOR_PROFILE_ID).userId(AUTHOR_USER_ID).build()));
        when(userRepository.findAllById(List.of(AUTHOR_USER_ID))).thenReturn(List.of(
                User.builder().id(AUTHOR_USER_ID).name(name).build()));
    }

    private JobApplication application(Long jobId, JobApplicationStatus status) {
        return JobApplication.builder().jobId(jobId).studentProfileId(STUDENT_PROFILE_ID).status(status).build();
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
        return proposal(id, createdAt, likeCount, ownerProfileId, AUTHOR_PROFILE_ID);
    }

    private Proposal proposal(Long id, LocalDateTime createdAt, int likeCount, Long ownerProfileId,
            Long studentProfileId) {
        return Proposal.builder()
                .id(id)
                .studentProfileId(studentProfileId)
                .ownerProfileId(ownerProfileId)
                .title("제안 " + id)
                .proposedSolution("해결 방안 " + id)
                .status(ProposalStatus.PENDING)
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
                .budget(300_000L)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .createdAt(createdAt)
                .build();
    }
}
