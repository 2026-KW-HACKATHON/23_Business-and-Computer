package com.gakkum.backend.application.home;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.home.controller.HomeController;
import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerClosedJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerOpenJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerProgressJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerReceivedProposal;
import com.gakkum.backend.application.home.dto.HomeQueryDto.PeerProposal;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentApplication;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentProgressJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentSentProposal;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentSettledJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentSettlements;
import com.gakkum.backend.application.home.facade.HomeFacade;
import com.gakkum.backend.application.home.service.HomeQueryService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("홈 본문 집계 전체 흐름 (GET /me/home)")
class HomeFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB6D";
    private static final Long OWNER_PROFILE_ID = 5L;
    private static final Long STUDENT_PROFILE_ID = 7L;
    private static final String DEMO_SESSION_ID = "01K58M6PJV8VAJMXHBHJ2PDEMO";

    private final UserService userService = mock(UserService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final StudentService studentService = mock(StudentService.class);
    private final HomeQueryService homeQueryService = mock(HomeQueryService.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HomeFacade facade = new HomeFacade(userService, ownerService, studentService, homeQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(new HomeController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("사장님에게 확인할 일·진행·대기·완료를 분류하고 정해진 순서와 이동 ID, 한국 날짜로 반환한다")
    void returnsOwnerHome() throws Exception {
        givenOwner();
        when(homeQueryService.getOwnerProgressJobs(OWNER_PROFILE_ID)).thenReturn(List.of(
                // 수정안 도착. UTC 10-01 16:00은 한국 10-02라 자동 완료 예정일은 10-09
                ownerProgress(job(11L, JobStatus.MATCHED, 77L, "2026-10-10", "2026-10-20"),
                        submission(JobSubmissionType.REVISION, JobSubmissionReviewStatus.PENDING,
                                LocalDateTime.of(2026, 10, 1, 16, 0)),
                        JobProgressStage.REVISION),
                // 초안 제작 중: 초안 마감 10-15
                ownerProgress(job(12L, JobStatus.MATCHED, null, "2026-10-15", "2026-10-30"), null,
                        JobProgressStage.STARTED),
                // 수정안 제작 중: 최종 마감 10-12
                ownerProgress(job(13L, JobStatus.MATCHED, null, "2026-10-01", "2026-10-12"), null,
                        JobProgressStage.REVISION)));
        when(homeQueryService.getOwnerReceivedProposals(OWNER_PROFILE_ID)).thenReturn(List.of(
                OwnerReceivedProposal.of(proposal(21L, ProposalStatus.PENDING, 4), "박제안", "미디어학부", "마케팅"),
                OwnerReceivedProposal.of(proposal(22L, ProposalStatus.REJECTED, 9), "최거절", "경영학부", "디자인")));
        when(homeQueryService.getOwnerOpenJobs(OWNER_PROFILE_ID)).thenReturn(List.of(
                OwnerOpenJob.of(job(31L, JobStatus.OPEN, null, "2026-10-25", "2026-11-05"), 2, "개발"),
                OwnerOpenJob.of(job(32L, JobStatus.OPEN, null, "2026-10-18", "2026-11-01"), 0, null),
                OwnerOpenJob.of(job(33L, JobStatus.OPEN, null, "2026-10-11", "2026-11-01"), 0, "개발")));
        when(homeQueryService.getOwnerClosedJobs(OWNER_PROFILE_ID)).thenReturn(List.of(
                // UTC 09-30 15:30은 한국 10-01
                OwnerClosedJob.of(closedJob(41L, JobStatus.CLOSED, 78L, LocalDateTime.of(2026, 9, 30, 15, 30)),
                        "김완료"),
                OwnerClosedJob.of(closedJob(42L, JobStatus.CANCELLED, null, LocalDateTime.of(2026, 9, 20, 3, 0)),
                        null)));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("OWNER"))
                .andExpect(jsonPath("$.data.firstVisit").value(false))
                .andExpect(jsonPath("$.data.todos.length()").value(3))
                .andExpect(jsonPath("$.data.todos[0].type").value("draftArrived"))
                .andExpect(jsonPath("$.data.todos[0].kind").value("proposal"))
                .andExpect(jsonPath("$.data.todos[0].jobId").value(11))
                .andExpect(jsonPath("$.data.todos[0].proposalId").value(77))
                .andExpect(jsonPath("$.data.todos[0].title").value("의뢰 11"))
                .andExpect(jsonPath("$.data.todos[0].field").value("디자인"))
                .andExpect(jsonPath("$.data.todos[0].student.name").value("김학생"))
                .andExpect(jsonPath("$.data.todos[0].student.major").value("소프트웨어학부"))
                .andExpect(jsonPath("$.data.todos[0].revision").value(true))
                .andExpect(jsonPath("$.data.todos[0].autoCompleteOn").value("2026-10-09"))
                .andExpect(jsonPath("$.data.todos[0].applicantCount").doesNotExist())
                .andExpect(jsonPath("$.data.todos[1].type").value("proposalArrived"))
                .andExpect(jsonPath("$.data.todos[1].kind").value("proposal"))
                .andExpect(jsonPath("$.data.todos[1].proposalId").value(21))
                .andExpect(jsonPath("$.data.todos[1].jobId").doesNotExist())
                .andExpect(jsonPath("$.data.todos[1].student.name").value("박제안"))
                .andExpect(jsonPath("$.data.todos[1].likeCount").value(4))
                .andExpect(jsonPath("$.data.todos[2].type").value("applicants"))
                .andExpect(jsonPath("$.data.todos[2].kind").value("request"))
                .andExpect(jsonPath("$.data.todos[2].jobId").value(31))
                .andExpect(jsonPath("$.data.todos[2].applicantCount").value(2))
                .andExpect(jsonPath("$.data.todos[2].draftDeadline").value("2026-10-25"))
                .andExpect(jsonPath("$.data.todos[2].student").doesNotExist())
                .andExpect(jsonPath("$.data.working.length()").value(2))
                .andExpect(jsonPath("$.data.working[0].jobId").value(13))
                .andExpect(jsonPath("$.data.working[0].kind").value("request"))
                .andExpect(jsonPath("$.data.working[0].proposalId").doesNotExist())
                .andExpect(jsonPath("$.data.working[0].stage").value("final"))
                .andExpect(jsonPath("$.data.working[0].due").value("2026-10-12"))
                .andExpect(jsonPath("$.data.working[0].studentName").value("김학생"))
                .andExpect(jsonPath("$.data.working[1].jobId").value(12))
                .andExpect(jsonPath("$.data.working[1].stage").value("draft"))
                .andExpect(jsonPath("$.data.working[1].due").value("2026-10-15"))
                .andExpect(jsonPath("$.data.waiting.length()").value(2))
                .andExpect(jsonPath("$.data.waiting[0].jobId").value(33))
                .andExpect(jsonPath("$.data.waiting[0].kind").value("request"))
                .andExpect(jsonPath("$.data.waiting[0].draftDeadline").value("2026-10-11"))
                .andExpect(jsonPath("$.data.waiting[1].jobId").value(32))
                .andExpect(jsonPath("$.data.done.length()").value(1))
                .andExpect(jsonPath("$.data.done[0].jobId").value(41))
                .andExpect(jsonPath("$.data.done[0].kind").value("proposal"))
                .andExpect(jsonPath("$.data.done[0].proposalId").value(78))
                .andExpect(jsonPath("$.data.done[0].studentName").value("김완료"))
                .andExpect(jsonPath("$.data.done[0].completedOn").value("2026-10-01"));
        verifyNoInteractions(studentService);
    }

    @Test
    @DisplayName("사장님의 모든 조회가 비어 있으면 섹션은 빈 배열이고 firstVisit은 true다")
    void returnsEmptyArraysAndFirstVisitForNewOwner() throws Exception {
        givenOwner();

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstVisit").value(true))
                .andExpect(jsonPath("$.data.todos").isArray())
                .andExpect(jsonPath("$.data.todos").isEmpty())
                .andExpect(jsonPath("$.data.working").isEmpty())
                .andExpect(jsonPath("$.data.waiting").isEmpty())
                .andExpect(jsonPath("$.data.done").isEmpty());
    }

    @Test
    @DisplayName("취소된 의뢰만 있어도 이력이 있으므로 firstVisit은 false이고 완료 섹션에는 내리지 않는다")
    void countsCancelledJobAsHistory() throws Exception {
        givenOwner();
        when(homeQueryService.getOwnerClosedJobs(OWNER_PROFILE_ID)).thenReturn(List.of(
                OwnerClosedJob.of(closedJob(42L, JobStatus.CANCELLED, null, LocalDateTime.of(2026, 9, 20, 3, 0)),
                        null)));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstVisit").value(false))
                .andExpect(jsonPath("$.data.done").isEmpty());
    }

    @Test
    @DisplayName("받은 제안 조회가 실패하면 사장님 todos만 명시적 null이고 독립 섹션은 유지하며, 이력을 못 봤으면 firstVisit도 null이다")
    void nullsOnlyOwnerTodosWhenProposalsFail() throws Exception {
        givenOwner();
        when(homeQueryService.getOwnerReceivedProposals(OWNER_PROFILE_ID))
                .thenThrow(new DataAccessResourceFailureException("connection lost"));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasKey("todos")))
                .andExpect(jsonPath("$.data.todos").value(nullValue()))
                .andExpect(jsonPath("$.data", hasKey("firstVisit")))
                .andExpect(jsonPath("$.data.firstVisit").value(nullValue()))
                .andExpect(jsonPath("$.data.working").isArray())
                .andExpect(jsonPath("$.data.waiting").isArray())
                .andExpect(jsonPath("$.data.done").isArray());
    }

    @Test
    @DisplayName("일부 조회가 실패해도 성공한 조회에서 이력이 보이면 firstVisit은 false다")
    void firstVisitIsFalseWhenHistoryConfirmedDespiteFailure() throws Exception {
        givenOwner();
        when(homeQueryService.getOwnerReceivedProposals(OWNER_PROFILE_ID))
                .thenThrow(new DataAccessResourceFailureException("connection lost"));
        when(homeQueryService.getOwnerOpenJobs(OWNER_PROFILE_ID)).thenReturn(List.of(
                OwnerOpenJob.of(job(32L, JobStatus.OPEN, null, "2026-10-18", "2026-11-01"), 0, null)));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstVisit").value(false))
                .andExpect(jsonPath("$.data.todos").value(nullValue()))
                .andExpect(jsonPath("$.data.waiting.length()").value(1));
    }

    @Test
    @DisplayName("진행 조회가 실패하면 사장님 todos와 working이 함께 null이 된다")
    void nullsOwnerTodosAndWorkingWhenProgressFails() throws Exception {
        givenOwner();
        when(homeQueryService.getOwnerProgressJobs(OWNER_PROFILE_ID))
                .thenThrow(new IllegalStateException("Specialty not found: 3"));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("todos")))
                .andExpect(jsonPath("$.data.todos").value(nullValue()))
                .andExpect(jsonPath("$.data", hasKey("working")))
                .andExpect(jsonPath("$.data.working").value(nullValue()))
                .andExpect(jsonPath("$.data.waiting").isArray())
                .andExpect(jsonPath("$.data.done").isArray());
    }

    @Test
    @DisplayName("모든 데이터 섹션 조회가 실패하면 부분 성공이 아니라 공통 500으로 응답한다")
    void returnsServerErrorWhenEverySectionFails() throws Exception {
        givenOwner();
        when(homeQueryService.getOwnerOpenJobs(OWNER_PROFILE_ID)).thenThrow(new IllegalStateException("boom"));
        when(homeQueryService.getOwnerProgressJobs(OWNER_PROFILE_ID)).thenThrow(new IllegalStateException("boom"));
        when(homeQueryService.getOwnerClosedJobs(OWNER_PROFILE_ID)).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("학생에게 확인할 일·확인 중·대기·다른 학생 제안·완료를 분류하고 정해진 순서와 이동 ID, 한국 날짜로 반환한다")
    void returnsStudentHome() throws Exception {
        givenStudent();
        Job agreementJob = job(51L, JobStatus.AWAITING_START, 61L, "2026-10-14", "2026-10-28");
        when(homeQueryService.getStudentSentProposals(STUDENT_PROFILE_ID)).thenReturn(List.of(
                StudentSentProposal.of(proposal(61L, ProposalStatus.AWAITING_START, 3), "가꿈 카페",
                        List.of("디자인", "마케팅"), agreementJob),
                // 의뢰서가 왔지만 사장님이 취소한 제안은 확인할 일이 아니다
                StudentSentProposal.of(proposal(62L, ProposalStatus.AWAITING_START, 1), "공룡 분식", List.of(),
                        job(52L, JobStatus.CANCELLED, 62L, "2026-10-14", "2026-10-28")),
                StudentSentProposal.of(proposal(63L, ProposalStatus.PENDING, 8), "월계 서점", List.of("개발"), null),
                StudentSentProposal.of(proposal(64L, ProposalStatus.REJECTED, 0), "월계 서점", List.of(), null)));
        when(homeQueryService.getStudentProgressJobs(STUDENT_PROFILE_ID)).thenReturn(List.of(
                // 초안 차례: 초안 마감 10-20
                StudentProgressJob.of(job(71L, JobStatus.MATCHED, null, "2026-10-20", "2026-10-30"), null,
                        "가꿈 카페", List.of("개발")),
                // 수정안 차례: 최종 마감 10-16
                StudentProgressJob.of(job(72L, JobStatus.MATCHED, 65L, "2026-10-05", "2026-10-16"),
                        submission(JobSubmissionType.DRAFT, JobSubmissionReviewStatus.REVISION_REQUESTED,
                                LocalDateTime.of(2026, 10, 4, 1, 0)),
                        "공룡 분식", List.of("디자인")),
                // 수정안을 내고 확인 대기. UTC 10-08 15:10은 한국 10-09
                StudentProgressJob.of(job(73L, JobStatus.MATCHED, null, "2026-10-05", "2026-10-16"),
                        submission(JobSubmissionType.REVISION, JobSubmissionReviewStatus.PENDING,
                                LocalDateTime.of(2026, 10, 8, 15, 10)),
                        "월계 서점", List.of())));
        when(homeQueryService.getStudentApplications(STUDENT_PROFILE_ID, DEMO_SESSION_ID)).thenReturn(List.of(
                StudentApplication.of(job(81L, JobStatus.OPEN, null, "2026-10-22", "2026-11-02"),
                        JobApplication.builder().id(91L).jobId(81L).build(), true, "가꿈 카페"),
                StudentApplication.of(job(82L, JobStatus.MATCHED, null, "2026-10-22", "2026-11-02"),
                        JobApplication.builder().id(92L).jobId(82L).build(), false, "공룡 분식")));
        when(homeQueryService.getStudentSettlements(USERNAME)).thenReturn(StudentSettlements.of(true, List.of(
                StudentSettledJob.of(101L, null, "지난 의뢰", "가꿈 카페", LocalDate.of(2026, 9, 12)),
                StudentSettledJob.of(102L, 66L, "지난 제안", "공룡 분식", LocalDate.of(2026, 9, 28)))));
        when(homeQueryService.getPeerProposals(STUDENT_PROFILE_ID, DEMO_SESSION_ID)).thenReturn(List.of(
                PeerProposal.of(proposal(111L, ProposalStatus.PENDING, 12), "이친구", "월계 서점", true)));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.firstVisit").value(false))
                .andExpect(jsonPath("$.data.todos.length()").value(3))
                .andExpect(jsonPath("$.data.todos[0].type").value("proposalAgreement"))
                .andExpect(jsonPath("$.data.todos[0].kind").value("proposal"))
                .andExpect(jsonPath("$.data.todos[0].proposalId").value(61))
                .andExpect(jsonPath("$.data.todos[0].jobId").value(51))
                .andExpect(jsonPath("$.data.todos[0].title").value("제안 61"))
                .andExpect(jsonPath("$.data.todos[0].storeName").value("가꿈 카페"))
                .andExpect(jsonPath("$.data.todos[0].categories[1]").value("마케팅"))
                .andExpect(jsonPath("$.data.todos[0].stage").value("draft"))
                .andExpect(jsonPath("$.data.todos[0].due").value("2026-10-14"))
                .andExpect(jsonPath("$.data.todos[1].type").value("revising"))
                .andExpect(jsonPath("$.data.todos[1].jobId").value(72))
                .andExpect(jsonPath("$.data.todos[1].kind").value("proposal"))
                .andExpect(jsonPath("$.data.todos[1].proposalId").value(65))
                .andExpect(jsonPath("$.data.todos[1].stage").value("final"))
                .andExpect(jsonPath("$.data.todos[1].due").value("2026-10-16"))
                .andExpect(jsonPath("$.data.todos[2].type").value("drafting"))
                .andExpect(jsonPath("$.data.todos[2].jobId").value(71))
                .andExpect(jsonPath("$.data.todos[2].kind").value("request"))
                .andExpect(jsonPath("$.data.todos[2].proposalId").doesNotExist())
                .andExpect(jsonPath("$.data.todos[2].stage").value("draft"))
                .andExpect(jsonPath("$.data.todos[2].due").value("2026-10-20"))
                .andExpect(jsonPath("$.data.checking.length()").value(1))
                .andExpect(jsonPath("$.data.checking[0].jobId").value(73))
                .andExpect(jsonPath("$.data.checking[0].kind").value("request"))
                .andExpect(jsonPath("$.data.checking[0].storeName").value("월계 서점"))
                .andExpect(jsonPath("$.data.checking[0].submissionType").value("REVISION"))
                .andExpect(jsonPath("$.data.checking[0].submittedOn").value("2026-10-09"))
                .andExpect(jsonPath("$.data.waiting.length()").value(2))
                .andExpect(jsonPath("$.data.waiting[0].type").value("proposal"))
                .andExpect(jsonPath("$.data.waiting[0].proposalId").value(63))
                .andExpect(jsonPath("$.data.waiting[0].storeName").value("월계 서점"))
                .andExpect(jsonPath("$.data.waiting[0].likeCount").value(8))
                .andExpect(jsonPath("$.data.waiting[1].type").value("application"))
                .andExpect(jsonPath("$.data.waiting[1].jobId").value(81))
                .andExpect(jsonPath("$.data.waiting[1].jobApplicationId").value(91))
                .andExpect(jsonPath("$.data.waiting[1].draftDeadline").value("2026-10-22"))
                .andExpect(jsonPath("$.data.peerProposals.length()").value(1))
                .andExpect(jsonPath("$.data.peerProposals[0].proposalId").value(111))
                .andExpect(jsonPath("$.data.peerProposals[0].studentName").value("이친구"))
                .andExpect(jsonPath("$.data.peerProposals[0].storeName").value("월계 서점"))
                .andExpect(jsonPath("$.data.peerProposals[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.peerProposals[0].likeCount").value(12))
                .andExpect(jsonPath("$.data.peerProposals[0].likedByMe").value(true))
                .andExpect(jsonPath("$.data.done.length()").value(2))
                .andExpect(jsonPath("$.data.done[0].jobId").value(102))
                .andExpect(jsonPath("$.data.done[0].kind").value("proposal"))
                .andExpect(jsonPath("$.data.done[0].proposalId").value(66))
                .andExpect(jsonPath("$.data.done[0].completedOn").value("2026-09-28"))
                .andExpect(jsonPath("$.data.done[1].jobId").value(101))
                .andExpect(jsonPath("$.data.done[1].kind").value("request"));
        verifyNoInteractions(ownerService);
    }

    @Test
    @DisplayName("학생의 개인 이력 조회가 모두 비어 있으면 다른 학생 제안이 있어도 firstVisit은 true다")
    void ignoresPeerProposalsForStudentFirstVisit() throws Exception {
        givenStudent();
        when(homeQueryService.getPeerProposals(STUDENT_PROFILE_ID, DEMO_SESSION_ID)).thenReturn(List.of(
                PeerProposal.of(proposal(111L, ProposalStatus.PENDING, 12), "이친구", "월계 서점", false)));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstVisit").value(true))
                .andExpect(jsonPath("$.data.todos").isEmpty())
                .andExpect(jsonPath("$.data.checking").isEmpty())
                .andExpect(jsonPath("$.data.waiting").isEmpty())
                .andExpect(jsonPath("$.data.done").isEmpty())
                .andExpect(jsonPath("$.data.peerProposals.length()").value(1));
    }

    @Test
    @DisplayName("보낸 제안 조회가 실패하면 학생 todos와 waiting이 null이고 checking·peerProposals·done은 유지한다")
    void nullsStudentTodosAndWaitingWhenProposalsFail() throws Exception {
        givenStudent();
        when(homeQueryService.getStudentSentProposals(STUDENT_PROFILE_ID))
                .thenThrow(new DataAccessResourceFailureException("connection lost"));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("todos")))
                .andExpect(jsonPath("$.data.todos").value(nullValue()))
                .andExpect(jsonPath("$.data", hasKey("waiting")))
                .andExpect(jsonPath("$.data.waiting").value(nullValue()))
                .andExpect(jsonPath("$.data.firstVisit").value(nullValue()))
                .andExpect(jsonPath("$.data.checking").isArray())
                .andExpect(jsonPath("$.data.peerProposals").isArray())
                .andExpect(jsonPath("$.data.done").isArray());
    }

    @Test
    @DisplayName("지원 조회가 실패하면 학생 waiting만 null이 된다")
    void nullsStudentWaitingWhenApplicationsFail() throws Exception {
        givenStudent();
        when(homeQueryService.getStudentApplications(STUDENT_PROFILE_ID, DEMO_SESSION_ID))
                .thenThrow(new DataAccessResourceFailureException("connection lost"));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("waiting")))
                .andExpect(jsonPath("$.data.waiting").value(nullValue()))
                .andExpect(jsonPath("$.data.todos").isArray())
                .andExpect(jsonPath("$.data.checking").isArray())
                .andExpect(jsonPath("$.data.peerProposals").isArray())
                .andExpect(jsonPath("$.data.done").isArray());
    }

    @Test
    @DisplayName("진행 조회가 실패하면 학생 todos와 checking이 함께 null이 된다")
    void nullsStudentTodosAndCheckingWhenProgressFails() throws Exception {
        givenStudent();
        when(homeQueryService.getStudentProgressJobs(STUDENT_PROFILE_ID))
                .thenThrow(new DataAccessResourceFailureException("connection lost"));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("todos")))
                .andExpect(jsonPath("$.data.todos").value(nullValue()))
                .andExpect(jsonPath("$.data", hasKey("checking")))
                .andExpect(jsonPath("$.data.checking").value(nullValue()))
                .andExpect(jsonPath("$.data.waiting").isArray())
                .andExpect(jsonPath("$.data.done").isArray());
    }

    @Test
    @DisplayName("정산 조회가 실패해도 정산 내역이 아닌 이력이 보이면 firstVisit은 false이고 done만 null이다")
    void nullsStudentDoneWhenSettlementsFail() throws Exception {
        givenStudent();
        when(homeQueryService.getStudentSettlements(USERNAME))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        when(homeQueryService.getStudentApplications(STUDENT_PROFILE_ID, DEMO_SESSION_ID)).thenReturn(List.of(
                StudentApplication.of(job(82L, JobStatus.MATCHED, null, "2026-10-22", "2026-11-02"),
                        JobApplication.builder().id(92L).jobId(82L).build(), false, "공룡 분식")));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstVisit").value(false))
                .andExpect(jsonPath("$.data", hasKey("done")))
                .andExpect(jsonPath("$.data.done").value(nullValue()))
                .andExpect(jsonPath("$.data.waiting").isEmpty());
    }

    @Test
    @DisplayName("가입을 끝내지 않은 PENDING 사용자는 조회 없이 403 HOME_403으로 거부한다")
    void rejectsPendingUser() throws Exception {
        when(userService.getActiveUser(USERNAME)).thenReturn(user(UserRole.PENDING));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("HOME_403"));
        verifyNoInteractions(homeQueryService);
    }

    @Test
    @DisplayName("활성 사용자가 아니면 조회 없이 401로 거부한다")
    void rejectsInactiveUser() throws Exception {
        when(userService.getActiveUser(USERNAME)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(homeQueryService);
    }

    @Test
    @DisplayName("사장님 프로필이 없으면 부분 실패로 숨기지 않고 403으로 거부한다")
    void rejectsOwnerWithoutProfile() throws Exception {
        when(userService.getActiveUser(USERNAME)).thenReturn(user(UserRole.OWNER));
        when(ownerService.getOwnerProfile(USER_ID)).thenThrow(new BusinessException(ErrorCode.OWNER_PROFILE_NOT_FOUND));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OWNER_403"));
        verifyNoInteractions(homeQueryService);
    }

    @Test
    @DisplayName("원천 조회가 인증·권한 거부를 던지면 섹션 null로 숨기지 않고 그대로 응답한다")
    void propagatesAccessDenialFromSource() throws Exception {
        givenStudent();
        when(homeQueryService.getStudentSettlements(any()))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/me/home").principal(authentication))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    private void givenOwner() {
        when(userService.getActiveUser(USERNAME)).thenReturn(user(UserRole.OWNER));
        when(ownerService.getOwnerProfile(USER_ID)).thenReturn(Owner.builder().id(OWNER_PROFILE_ID).build());
    }

    private void givenStudent() {
        when(userService.getActiveUser(USERNAME)).thenReturn(user(UserRole.STUDENT));
        when(studentService.findStudentProfileByUserId(USER_ID))
                .thenReturn(Optional.of(Student.builder().id(STUDENT_PROFILE_ID).build()));
        when(homeQueryService.getStudentSettlements(USERNAME)).thenReturn(StudentSettlements.of(false, List.of()));
    }

    private static User user(UserRole role) {
        return User.builder()
                .id(USER_ID)
                .username(USERNAME)
                .role(role)
                .demoSessionId(role == UserRole.STUDENT ? DEMO_SESSION_ID : null)
                .build();
    }

    private static OwnerProgressJob ownerProgress(Job job, JobSubmission pendingSubmission, JobProgressStage stage) {
        return OwnerProgressJob.of(job, pendingSubmission, stage, "김학생", "소프트웨어학부", "디자인");
    }

    private static Job job(Long id, JobStatus status, Long proposalId, String draftDeadline, String finalDeadline) {
        return Job.builder()
                .id(id)
                .title("의뢰 " + id)
                .status(status)
                .proposalId(proposalId)
                .draftDeadline(LocalDate.parse(draftDeadline))
                .finalDeadline(LocalDate.parse(finalDeadline))
                .build();
    }

    private static Job closedJob(Long id, JobStatus status, Long proposalId, LocalDateTime completedAt) {
        return Job.builder()
                .id(id)
                .title("의뢰 " + id)
                .status(status)
                .proposalId(proposalId)
                .completedAt(completedAt)
                .build();
    }

    private static JobSubmission submission(JobSubmissionType type, JobSubmissionReviewStatus reviewStatus,
            LocalDateTime createdAt) {
        return JobSubmission.builder()
                .submissionType(type)
                .reviewStatus(reviewStatus)
                .createdAt(createdAt)
                .build();
    }

    private static Proposal proposal(Long id, ProposalStatus status, int likeCount) {
        return Proposal.builder()
                .id(id)
                .title("제안 " + id)
                .status(status)
                .likeCount(likeCount)
                .build();
    }
}
