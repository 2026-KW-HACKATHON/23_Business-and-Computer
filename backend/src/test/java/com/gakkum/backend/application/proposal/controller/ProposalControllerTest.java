package com.gakkum.backend.application.proposal.controller;

import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalJobDeclineResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.RefundedPaymentData;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.proposal.facade.ProposalFacade;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.StartProposalJobCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalAgreementResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalJobStartResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalLikeResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.MyProposalListResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.MyProposalResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCreateResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ReceivedProposalListResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ReceivedProposalResult;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.SpecialtyResult;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCancelResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalRejectResult;
import com.gakkum.backend.domain.proposal.entity.ProposalRejectedBy;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("제안 컨트롤러 (POST /proposals, GET /proposals/{proposalId})")
class ProposalControllerTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String IMAGE_URL =
            "https://bucket.s3.ap-northeast-2.amazonaws.com/images/proposal/u/0b4f2a3e-6a8c-4a39-9f55-8f1d8f0b2c11.png";
    private static final String TEXT_500 = "가".repeat(500);

    private final ProposalFacade proposalFacade = mock(ProposalFacade.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProposalController(proposalFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("제안 전송은 201과 제안 ID를 반환하고 앞뒤 공백을 제거한 값을 명령으로 넘긴다")
    void returnsCreatedProposal() throws Exception {
        when(proposalFacade.createProposal(any()))
                .thenReturn(ProposalCreateResult.from(Proposal.builder().id(31L).build()));

        mockMvc.perform(post("/proposals").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[1, 2]", "\"  메뉴판 개선 제안  \"", "\"" + TEXT_500 + "\"", "50000", "0", "0",
                                "[\"" + IMAGE_URL + "\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.proposalId").value(31));

        ArgumentCaptor<CreateProposalCommand> captor = ArgumentCaptor.forClass(CreateProposalCommand.class);
        verify(proposalFacade).createProposal(captor.capture());
        CreateProposalCommand command = captor.getValue();
        assertThat(command.getUsername()).isEqualTo(USERNAME);
        assertThat(command.getOwnerProfileId()).isEqualTo(5L);
        assertThat(command.getSpecialtyIds()).containsExactly(1L, 2L);
        assertThat(command.getTitle()).isEqualTo("메뉴판 개선 제안");
        assertThat(command.getCustomerProblem()).isEqualTo(TEXT_500);
        assertThat(command.getProposedSolution()).isEqualTo("사진 메뉴판으로 바꿉니다.");
        assertThat(command.getWorkPlan()).isEqualTo("촬영 후 편집합니다.");
        assertThat(command.getProposedFee()).isEqualTo(50000L);
        assertThat(command.getDraftDays()).isZero();
        assertThat(command.getFinalDays()).isZero();
        assertThat(command.getReferenceImageUrls()).containsExactly(IMAGE_URL);
    }

    @Test
    @DisplayName("사진을 보내지 않으면 빈 사진 목록으로 제안을 전송한다")
    void acceptsMissingImages() throws Exception {
        when(proposalFacade.createProposal(any()))
                .thenReturn(ProposalCreateResult.from(Proposal.builder().id(32L).build()));

        mockMvc.perform(post("/proposals").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[1]", "\"제목\"", "\"문제\"", "1", "3", "7", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.proposalId").value(32));

        ArgumentCaptor<CreateProposalCommand> captor = ArgumentCaptor.forClass(CreateProposalCommand.class);
        verify(proposalFacade).createProposal(captor.capture());
        assertThat(captor.getValue().getReferenceImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("서로 다른 사진 URL 5장(2048자 URL 포함)까지는 제안을 전송한다")
    void acceptsFiveDistinctImages() throws Exception {
        when(proposalFacade.createProposal(any()))
                .thenReturn(ProposalCreateResult.from(Proposal.builder().id(33L).build()));
        String longUrl = "https://img.example.com/" + "a".repeat(2048 - 24);

        mockMvc.perform(post("/proposals").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[1]", "\"제목\"", "\"문제\"", "1", "0", "0",
                                imageUrls(4).replace("]", ",\"" + longUrl + "\"]"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.proposalId").value(33));

        ArgumentCaptor<CreateProposalCommand> captor = ArgumentCaptor.forClass(CreateProposalCommand.class);
        verify(proposalFacade).createProposal(captor.capture());
        assertThat(captor.getValue().getReferenceImageUrls()).hasSize(5).contains(longUrl);
    }

    /** 서로 다른 사진 URL count개를 JSON 배열 문자열로 만든다. */
    private static String imageUrls(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> "\"https://img.example.com/" + index + ".png\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("소분류 없음", body("[]", "\"제목\"", "\"문제\"", "1", "0", "0", null)),
                Arguments.of("소분류 누락", body(null, "\"제목\"", "\"문제\"", "1", "0", "0", null)),
                Arguments.of("소분류 ID 0", body("[0]", "\"제목\"", "\"문제\"", "1", "0", "0", null)),
                Arguments.of("빈 제목", body("[1]", "\"  \"", "\"문제\"", "1", "0", "0", null)),
                Arguments.of("256자 제목", body("[1]", "\"" + "a".repeat(256) + "\"", "\"문제\"", "1", "0", "0", null)),
                Arguments.of("501자 내용", body("[1]", "\"제목\"", "\"" + "가".repeat(501) + "\"", "1", "0", "0", null)),
                Arguments.of("빈 내용", body("[1]", "\"제목\"", "\"\"", "1", "0", "0", null)),
                Arguments.of("작업비 0", body("[1]", "\"제목\"", "\"문제\"", "0", "0", "0", null)),
                Arguments.of("작업비 누락", body("[1]", "\"제목\"", "\"문제\"", null, "0", "0", null)),
                Arguments.of("음수 초안 기간", body("[1]", "\"제목\"", "\"문제\"", "1", "-1", "0", null)),
                Arguments.of("최종 기간 누락", body("[1]", "\"제목\"", "\"문제\"", "1", "0", null, null)),
                Arguments.of("최종 기간이 초안보다 짧음", body("[1]", "\"제목\"", "\"문제\"", "1", "5", "4", null)),
                Arguments.of("빈 사진 URL", body("[1]", "\"제목\"", "\"문제\"", "1", "0", "0", "[\" \"]")),
                Arguments.of("사진 6장", body("[1]", "\"제목\"", "\"문제\"", "1", "0", "0", imageUrls(6))),
                Arguments.of("중복 사진 URL", body("[1]", "\"제목\"", "\"문제\"", "1", "0", "0",
                        "[\"https://img.example.com/a.png\",\"https://img.example.com/a.png\"]")),
                Arguments.of("2049자 사진 URL", body("[1]", "\"제목\"", "\"문제\"", "1", "0", "0",
                        "[\"https://img.example.com/" + "a".repeat(2049 - 24) + "\"]")),
                Arguments.of("사장님 ID 누락", "{\"specialtyIds\":[1],\"title\":\"제목\",\"customerProblem\":\"문제\","
                        + "\"proposedSolution\":\"해결\",\"workPlan\":\"계획\",\"proposedFee\":1,"
                        + "\"draftDays\":0,\"finalDays\":0}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("요청 형식이 잘못되면 COMMON_400으로 거부하고 파사드를 호출하지 않는다")
    void rejectsInvalidRequest(String caseName, String body) throws Exception {
        mockMvc.perform(post("/proposals").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(proposalFacade);
    }

    @Test
    @DisplayName("파사드의 비즈니스 오류는 공통 오류 응답 형식으로 반환한다")
    void returnsBusinessError() throws Exception {
        when(proposalFacade.createProposal(any()))
                .thenThrow(new BusinessException(ErrorCode.PROPOSAL_STUDENT_REQUIRED));

        mockMvc.perform(post("/proposals").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[1]", "\"제목\"", "\"문제\"", "1", "0", "0", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_403_STUDENT"));
    }

    @ParameterizedTest
    @CsvSource({ "2024402001, 24", "2001402001, 01", "1999402001, 99" })
    @DisplayName("제안 상세는 제안 내용과 학생 정보, 특기를 반환하고 학번은 입학년도 뒤 두 자리 문자열만 공개한다")
    void returnsProposalDetail(String studentNumber, String admissionYear) throws Exception {
        Proposal proposal = Proposal.builder()
                .id(31L)
                .title("메뉴판 개선 제안")
                .customerProblem("메뉴를 알아보기 어렵습니다.")
                .proposedSolution("사진 메뉴판으로 바꿉니다.")
                .workPlan("촬영 후 편집합니다.")
                .proposedFee(50000L)
                .draftDays(3)
                .finalDays(7)
                .referenceImageUrls(List.of(IMAGE_URL))
                .likeCount(4)
                .status(ProposalStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 9, 30, 10, 0))
                .build();
        Student student = Student.builder().id(7L).major("시각디자인학부").studentNumber(studentNumber)
                .profileImageUrl("https://cdn.example.com/students/7/profile.png").build();
        User studentUser = User.builder().name("김학생").build();
        List<SpecialtyCategoryResult> categories = List.of(
                SpecialtyCategoryResult.of(1L, "디자인", List.of(SpecialtyResult.of(3L, "로고 디자인"))),
                SpecialtyCategoryResult.of(2L, "영상", List.of(
                        SpecialtyResult.of(11L, "숏폼 촬영"), SpecialtyResult.of(12L, "영상 편집"))));
        when(proposalFacade.getProposalDetail(USERNAME, 31L))
                .thenReturn(ProposalDetailResult.of(proposal, "가게 이름", "서울시 마포구 1",
                        "https://cdn.example.com/owners/5/profile.png", student, studentUser,
                        new java.math.BigDecimal("4.3"), 5L, categories, true, LocalDate.of(2026, 10, 5), null, null));

        mockMvc.perform(get("/proposals/31").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.proposalId").value(31))
                .andExpect(jsonPath("$.data.title").value("메뉴판 개선 제안"))
                .andExpect(jsonPath("$.data.storeName").value("가게 이름"))
                .andExpect(jsonPath("$.data.storeAddress").value("서울시 마포구 1"))
                .andExpect(jsonPath("$.data.storeProfileImageUrl").value("https://cdn.example.com/owners/5/profile.png"))
                .andExpect(jsonPath("$.data.student.profileImageUrl")
                        .value("https://cdn.example.com/students/7/profile.png"))
                .andExpect(jsonPath("$.data.likeCount").value(4))
                .andExpect(jsonPath("$.data.likedByMe").value(true))
                .andExpect(jsonPath("$.data.specialtyCategories.length()").value(2))
                .andExpect(jsonPath("$.data.specialtyCategories[0].id").value(1))
                .andExpect(jsonPath("$.data.specialtyCategories[0].name").value("디자인"))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[0].id").value(3))
                .andExpect(jsonPath("$.data.specialtyCategories[0].specialties[0].name").value("로고 디자인"))
                .andExpect(jsonPath("$.data.specialtyCategories[1].specialties[1].id").value(12))
                .andExpect(jsonPath("$.data.specialtyCategories[1].specialties[1].name").value("영상 편집"))
                .andExpect(jsonPath("$.data.student.studentProfileId").value(7))
                .andExpect(jsonPath("$.data.student.name").value("김학생"))
                .andExpect(jsonPath("$.data.student.major").value("시각디자인학부"))
                .andExpect(jsonPath("$.data.student.studentNumber").value(admissionYear))
                .andExpect(jsonPath("$.data.student.averageRating").value(4.3))
                .andExpect(jsonPath("$.data.student.completedJobCount").value(5))
                .andExpect(jsonPath("$.data.customerProblem").value("메뉴를 알아보기 어렵습니다."))
                .andExpect(jsonPath("$.data.proposedSolution").value("사진 메뉴판으로 바꿉니다."))
                .andExpect(jsonPath("$.data.workPlan").value("촬영 후 편집합니다."))
                .andExpect(jsonPath("$.data.proposedFee").value(50000))
                .andExpect(jsonPath("$.data.finalDays").value(7))
                .andExpect(jsonPath("$.data.draftDays").value(3))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.estimatedDraftDeadline").value("2026-10-08"))
                .andExpect(jsonPath("$.data.estimatedFinalDeadline").value("2026-10-12"))
                .andExpect(jsonPath("$.data.jobId").doesNotExist())
                .andExpect(jsonPath("$.data.agreement").doesNotExist())
                .andExpect(jsonPath("$.data.referenceImageUrls[0]").value(IMAGE_URL))
                // UTC 10:00 → 한국 19:00. 한 번만 변환하고 +09:00을 붙인다
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-30T19:00:00+09:00"));
    }

    @Test
    @DisplayName("결제된 제안 상세는 연결된 의뢰 ID와 확정 작업 조건을 반환하고 예상 마감일은 내리지 않는다")
    void returnsPaidProposalDetailWithAgreement() throws Exception {
        Proposal proposal = Proposal.builder().id(31L).title("메뉴판 개선 제안").proposedFee(50000L)
                .draftDays(3).finalDays(7).referenceImageUrls(List.of()).likeCount(0)
                .status(ProposalStatus.AWAITING_START).build();
        Job job = Job.builder().id(42L).status(JobStatus.AWAITING_START).budget(120000L)
                .draftDeadline(LocalDate.of(2026, 10, 8)).finalDeadline(LocalDate.of(2026, 10, 12))
                .revisionCount(2).acceptanceMessage("매장 분위기에 맞춰 작업 부탁드립니다.").build();
        when(proposalFacade.getProposalDetail(USERNAME, 31L))
                .thenReturn(ProposalDetailResult.of(proposal, "가게 이름", null, null,
                        Student.builder().id(7L).build(), User.builder().name("김학생").build(),
                        new java.math.BigDecimal("4.3"), 5L, List.of(), false, LocalDate.of(2026, 10, 9), 42L,
                        ProposalAgreementResult.of(job, Instant.parse("2026-10-05T03:00:00Z"))));

        mockMvc.perform(get("/proposals/31").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AWAITING_START"))
                .andExpect(jsonPath("$.data.jobId").value(42))
                // 공감하지 않았으면 필드를 빼지 않고 false로 내린다
                .andExpect(jsonPath("$.data.likedByMe").value(false))
                .andExpect(jsonPath("$.data.student.studentNumber").doesNotExist())
                // 주소·매장 사진·학생 사진 미등록과 생성 시각 없음은 null로 내린다
                .andExpect(jsonPath("$.data.storeAddress").doesNotExist())
                .andExpect(jsonPath("$.data", hasKey("storeProfileImageUrl")))
                .andExpect(jsonPath("$.data.storeProfileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.student", hasKey("profileImageUrl")))
                .andExpect(jsonPath("$.data.student.profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.estimatedDraftDeadline").doesNotExist())
                .andExpect(jsonPath("$.data.estimatedFinalDeadline").doesNotExist())
                .andExpect(jsonPath("$.data.agreement.jobStatus").value("AWAITING_START"))
                // 학생 희망 금액은 그대로 두고 확정 작업비는 실제 결제 금액으로 내린다
                .andExpect(jsonPath("$.data.proposedFee").value(50000))
                .andExpect(jsonPath("$.data.agreement.budget").value(120000))
                .andExpect(jsonPath("$.data.agreement.draftDeadline").value("2026-10-08"))
                .andExpect(jsonPath("$.data.agreement.finalDeadline").value("2026-10-12"))
                .andExpect(jsonPath("$.data.agreement.revisionCount").value(2))
                .andExpect(jsonPath("$.data.agreement.messageToStudent").value("매장 분위기에 맞춰 작업 부탁드립니다."))
                .andExpect(jsonPath("$.data.agreement.paidAt").value("2026-10-05T12:00:00+09:00"))
                .andExpect(jsonPath("$.data.agreement.startedAt").doesNotExist());
    }

    @Test
    @DisplayName("작업 시작 요청은 인증 사용자와 의뢰 ID를 전달하고 200과 시작 결과를 반환한다")
    void startsProposalJob() throws Exception {
        Proposal proposal = Proposal.builder().id(31L).status(ProposalStatus.ACCEPTED).build();
        Job job = Job.builder().id(42L).status(JobStatus.MATCHED)
                .startedAt(LocalDateTime.of(2026, 10, 6, 9, 30))
                .draftDeadline(LocalDate.of(2026, 10, 8)).finalDeadline(LocalDate.of(2026, 10, 12)).build();
        when(proposalFacade.startProposalJob(any()))
                .thenReturn(ProposalJobStartResult.of(job, proposal, "01K58M6PJV8VAJMXHBHJ2ROOM1"));

        mockMvc.perform(post("/jobs/42/start").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deadlineAndPenaltyAgreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(42))
                .andExpect(jsonPath("$.data.jobStatus").value("MATCHED"))
                .andExpect(jsonPath("$.data.proposalStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.startedAt").value("2026-10-06T18:30:00+09:00"))
                .andExpect(jsonPath("$.data.chatRoomId").value("01K58M6PJV8VAJMXHBHJ2ROOM1"))
                .andExpect(jsonPath("$.data.draftDeadline").value("2026-10-08"))
                .andExpect(jsonPath("$.data.finalDeadline").value("2026-10-12"));

        ArgumentCaptor<StartProposalJobCommand> captor = ArgumentCaptor.forClass(StartProposalJobCommand.class);
        verify(proposalFacade).startProposalJob(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo(USERNAME);
        assertThat(captor.getValue().getJobId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("마감일·패널티 동의가 없거나 거짓이면 400을 반환하고 작업을 시작하지 않는다")
    void rejectsStartWithoutAgreement() throws Exception {
        for (String body : new String[] { "{}", "{\"deadlineAndPenaltyAgreed\":false}",
                "{\"deadlineAndPenaltyAgreed\":null}" }) {
            mockMvc.perform(post("/jobs/42/start").principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        verify(proposalFacade, never()).startProposalJob(any());
    }

    @ParameterizedTest
    @MethodSource("startErrors")
    @DisplayName("작업 시작의 권한·대상·상태 오류는 각 오류 코드의 상태로 반환한다")
    void returnsStartErrors(ErrorCode errorCode, int status, String code) throws Exception {
        when(proposalFacade.startProposalJob(any())).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post("/jobs/42/start").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deadlineAndPenaltyAgreed\":true}"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.error.code").value(code));
    }

    static Stream<Arguments> startErrors() {
        return Stream.of(
                Arguments.of(ErrorCode.JOB_START_FORBIDDEN, 403, "JOB_START_403"),
                Arguments.of(ErrorCode.JOB_NOT_FOUND, 404, "JOB_404"),
                Arguments.of(ErrorCode.JOB_START_NOT_AVAILABLE, 409, "JOB_START_409"));
    }

    static Stream<Arguments> proposalDetailErrors() {
        return Stream.of(
                Arguments.of(ErrorCode.UNAUTHORIZED, 401, "COMMON_401"),
                Arguments.of(ErrorCode.PROPOSAL_NOT_FOUND, 404, "PROPOSAL_404"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("proposalDetailErrors")
    @DisplayName("제안 상세의 비활성 사용자·없는 제안 오류는 공통 오류 응답 형식으로 반환한다")
    void returnsProposalDetailError(ErrorCode errorCode, int status, String code) throws Exception {
        when(proposalFacade.getProposalDetail(USERNAME, 31L)).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(get("/proposals/31").principal(authentication))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code));
    }

    @Test
    @DisplayName("숫자가 아닌 제안 ID는 COMMON_400으로 거부하고 파사드를 호출하지 않는다")
    void rejectsInvalidProposalId() throws Exception {
        mockMvc.perform(get("/proposals/abc").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verify(proposalFacade, never()).getProposalDetail(anyString(), anyLong());
    }

    @Test
    @DisplayName("공감 추가는 인증 사용자와 제안 ID를 전달하고 200과 제안 ID·공감 수·likedByMe true를 반환한다")
    void likesProposal() throws Exception {
        when(proposalFacade.likeProposal(USERNAME, 31L)).thenReturn(
                ProposalLikeResult.of(Proposal.builder().id(31L).likeCount(5).build(), true));

        mockMvc.perform(post("/proposals/31/likes").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data.proposalId").value(31))
                .andExpect(jsonPath("$.data.likeCount").value(5))
                .andExpect(jsonPath("$.data.likedByMe").value(true));

        verify(proposalFacade).likeProposal(USERNAME, 31L);
        verify(proposalFacade, never()).unlikeProposal(anyString(), anyLong());
    }

    @Test
    @DisplayName("공감 취소는 인증 사용자와 제안 ID를 전달하고 200과 제안 ID·공감 수·likedByMe false를 반환한다")
    void unlikesProposal() throws Exception {
        when(proposalFacade.unlikeProposal(USERNAME, 31L)).thenReturn(
                ProposalLikeResult.of(Proposal.builder().id(31L).likeCount(0).build(), false));

        mockMvc.perform(delete("/proposals/31/likes").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data.proposalId").value(31))
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.likedByMe").value(false));

        verify(proposalFacade).unlikeProposal(USERNAME, 31L);
        verify(proposalFacade, never()).likeProposal(anyString(), anyLong());
    }

    @ParameterizedTest(name = "제안 ID {0}")
    @ValueSource(strings = { "abc", "0", "-1", "1.5" })
    @DisplayName("공감 추가·취소는 숫자가 아니거나 0 이하인 제안 ID를 COMMON_400으로 거부하고 파사드를 호출하지 않는다")
    void rejectsInvalidProposalIdForLike(String proposalId) throws Exception {
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.DELETE)) {
            mockMvc.perform(MockMvcRequestBuilders.request(method, "/proposals/" + proposalId + "/likes")
                            .principal(authentication))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        verifyNoInteractions(proposalFacade);
    }

    static Stream<Arguments> likeErrors() {
        return Stream.of(
                Arguments.of(ErrorCode.UNAUTHORIZED, 401, "COMMON_401"),
                Arguments.of(ErrorCode.PROPOSAL_LIKE_STUDENT_REQUIRED, 403, "PROPOSAL_403_LIKE_STUDENT"),
                Arguments.of(ErrorCode.PROPOSAL_NOT_FOUND, 404, "PROPOSAL_404"),
                Arguments.of(ErrorCode.PROPOSAL_LIKE_NOT_AVAILABLE, 409, "PROPOSAL_409_LIKE"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("likeErrors")
    @DisplayName("공감 추가·취소의 잠긴 사용자·학생 아님·없는 제안·거절된 제안 오류는 각 오류 코드의 상태로 반환한다")
    void returnsLikeErrors(ErrorCode errorCode, int status, String code) throws Exception {
        when(proposalFacade.likeProposal(USERNAME, 31L)).thenThrow(new BusinessException(errorCode));
        when(proposalFacade.unlikeProposal(USERNAME, 31L)).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post("/proposals/31/likes").principal(authentication))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code));
        mockMvc.perform(delete("/proposals/31/likes").principal(authentication))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code));
    }

    @Test
    @DisplayName("제안 취소는 본문 없이 인증 사용자와 제안 ID를 전달하고 200과 제안 ID·취소 상태만 반환한다")
    void cancelsProposal() throws Exception {
        when(proposalFacade.cancelProposal(USERNAME, 31L)).thenReturn(ProposalCancelResult.from(
                Proposal.builder().id(31L).status(ProposalStatus.CANCELLED).build()));

        // 이미 취소한 본인 제안의 반복 요청도 같은 200 응답이다
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/proposals/31/cancel").principal(authentication))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data.proposalId").value(31))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }

        verify(proposalFacade, org.mockito.Mockito.times(2)).cancelProposal(USERNAME, 31L);
    }

    @ParameterizedTest(name = "제안 ID {0}")
    @ValueSource(strings = { "abc", "0", "-1", "1.5" })
    @DisplayName("제안 취소는 숫자가 아니거나 0 이하인 제안 ID를 COMMON_400으로 거부하고 파사드를 호출하지 않는다")
    void rejectsInvalidProposalIdForCancel(String proposalId) throws Exception {
        mockMvc.perform(post("/proposals/" + proposalId + "/cancel").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(proposalFacade);
    }

    static Stream<Arguments> cancelErrors() {
        return Stream.of(
                Arguments.of(ErrorCode.UNAUTHORIZED, 401, "COMMON_401"),
                Arguments.of(ErrorCode.PROPOSAL_CANCEL_FORBIDDEN, 403, "PROPOSAL_403_CANCEL"),
                Arguments.of(ErrorCode.PROPOSAL_NOT_FOUND, 404, "PROPOSAL_404"),
                Arguments.of(ErrorCode.PROPOSAL_CANCEL_NOT_AVAILABLE, 409, "PROPOSAL_409_CANCEL"),
                Arguments.of(ErrorCode.PROPOSAL_CANCEL_PAYMENT_PENDING, 409, "PROPOSAL_409_CANCEL_PAYMENT_PENDING"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("cancelErrors")
    @DisplayName("제안 취소의 잠긴 사용자·작성자 아님·없는 제안·취소 불가 상태·결제 대기 오류는 각 오류 코드의 상태로 반환한다")
    void returnsCancelErrors(ErrorCode errorCode, int status, String code) throws Exception {
        when(proposalFacade.cancelProposal(USERNAME, 31L)).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post("/proposals/31/cancel").principal(authentication))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code));
    }

    @Test
    @DisplayName("제안 거절은 본문 없이 인증 사용자와 제안 ID를 전달하고 200과 제안 ID·거절 상태만 반환한다")
    void rejectsProposal() throws Exception {
        when(proposalFacade.rejectProposal(USERNAME, 31L)).thenReturn(ProposalRejectResult.from(
                Proposal.builder().id(31L).status(ProposalStatus.REJECTED).rejectedBy(ProposalRejectedBy.OWNER)
                        .rejectedAt(LocalDateTime.of(2026, 10, 5, 15, 30)).build()));

        // 이미 거절한 본인 제안의 반복 요청도 같은 200 응답이다
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/proposals/31/reject").principal(authentication))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data.proposalId").value(31))
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));
        }

        verify(proposalFacade, org.mockito.Mockito.times(2)).rejectProposal(USERNAME, 31L);
    }

    @ParameterizedTest(name = "제안 ID {0}")
    @ValueSource(strings = { "abc", "0", "-1", "1.5" })
    @DisplayName("제안 거절은 숫자가 아니거나 0 이하인 제안 ID를 COMMON_400으로 거부하고 파사드를 호출하지 않는다")
    void rejectsInvalidProposalIdForReject(String proposalId) throws Exception {
        mockMvc.perform(post("/proposals/" + proposalId + "/reject").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(proposalFacade);
    }

    static Stream<Arguments> rejectErrors() {
        return Stream.of(
                Arguments.of(ErrorCode.UNAUTHORIZED, 401, "COMMON_401"),
                Arguments.of(ErrorCode.PROPOSAL_REJECT_FORBIDDEN, 403, "PROPOSAL_403_REJECT"),
                Arguments.of(ErrorCode.PROPOSAL_NOT_FOUND, 404, "PROPOSAL_404"),
                Arguments.of(ErrorCode.PROPOSAL_REJECT_NOT_AVAILABLE, 409, "PROPOSAL_409_REJECT"),
                Arguments.of(ErrorCode.PROPOSAL_REJECT_PAYMENT_PENDING, 409, "PROPOSAL_409_REJECT_PAYMENT_PENDING"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("rejectErrors")
    @DisplayName("제안 거절의 잠긴 사용자·받은 사장님 아님·없는 제안·거절 불가 상태·결제 대기 오류는 각 오류 코드의 상태로 반환한다")
    void returnsRejectErrors(ErrorCode errorCode, int status, String code) throws Exception {
        when(proposalFacade.rejectProposal(USERNAME, 31L)).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post("/proposals/31/reject").principal(authentication))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code));
    }

    @Test
    @DisplayName("거절된 제안은 양쪽 목록과 상세에서 거절 주체와 같은 한국 시각의 거절 시각을 반환하고 UTC 오후 3시 이후는 다음 날이 된다")
    void returnsRejectionDetailsInListsAndDetail() throws Exception {
        // UTC 10월 5일 15:30 = 한국 10월 6일 00:30
        givenProposalInListsAndDetail(Proposal.builder().id(31L).title("메뉴판 개선 제안").likeCount(4)
                .draftDays(3).finalDays(7).referenceImageUrls(List.of()).status(ProposalStatus.REJECTED)
                .rejectedBy(ProposalRejectedBy.OWNER).rejectedAt(LocalDateTime.of(2026, 10, 5, 15, 30))
                .createdAt(LocalDateTime.of(2026, 10, 1, 1, 0)).build());

        for (String path : List.of("/me/proposals", "/me/received-proposals")) {
            mockMvc.perform(get(path).principal(authentication))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.proposals[0].status").value("REJECTED"))
                    .andExpect(jsonPath("$.data.proposals[0].likeCount").value(4))
                    .andExpect(jsonPath("$.data.proposals[0].rejectedBy").value("OWNER"))
                    .andExpect(jsonPath("$.data.proposals[0].rejectedAt").value("2026-10-06T00:30:00+09:00"));
        }
        mockMvc.perform(get("/proposals/31").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectedBy").value("OWNER"))
                .andExpect(jsonPath("$.data.rejectedAt").value("2026-10-06T00:30:00+09:00"))
                .andExpect(jsonPath("$.data.estimatedDraftDeadline").value(nullValue()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ProposalStatus.class, names = { "PENDING", "REJECTED" })
    @DisplayName("거절되지 않았거나 거절 기록이 없는 기존 거절 제안은 양쪽 목록과 상세에서 거절 주체와 거절 시각을 null로 반환한다")
    void returnsNullRejectionDetailsWithoutRecord(ProposalStatus status) throws Exception {
        givenProposalInListsAndDetail(Proposal.builder().id(31L).title("메뉴판 개선 제안").likeCount(0)
                .draftDays(3).finalDays(7).referenceImageUrls(List.of()).status(status)
                .createdAt(LocalDateTime.of(2026, 10, 1, 1, 0)).build());

        for (String path : List.of("/me/proposals", "/me/received-proposals")) {
            mockMvc.perform(get(path).principal(authentication))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.proposals[0].status").value(status.name()))
                    .andExpect(jsonPath("$.data.proposals[0].rejectedBy").value(nullValue()))
                    .andExpect(jsonPath("$.data.proposals[0].rejectedAt").value(nullValue()));
        }
        mockMvc.perform(get("/proposals/31").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rejectedBy").value(nullValue()))
                .andExpect(jsonPath("$.data.rejectedAt").value(nullValue()));
    }

    // 같은 제안을 보낸 제안 목록·받은 제안 목록·상세가 모두 내리게 한다
    private void givenProposalInListsAndDetail(Proposal proposal) {
        Student student = Student.builder().id(7L).studentNumber("2024123456").build();
        User studentUser = User.builder().name("김학생").build();
        when(proposalFacade.getMyProposals(USERNAME)).thenReturn(MyProposalListResult.of(List.of(
                MyProposalResult.of(proposal, Owner.builder().id(50L).storeName("가꿈 카페").build(),
                        List.of(), null))));
        when(proposalFacade.getReceivedProposals(USERNAME)).thenReturn(ReceivedProposalListResult.of(List.of(
                ReceivedProposalResult.of(proposal, student, studentUser, List.of(), null))));
        when(proposalFacade.getProposalDetail(USERNAME, 31L))
                .thenReturn(ProposalDetailResult.of(proposal, "가꿈 카페", null, null, student, studentUser,
                        new java.math.BigDecimal("4.3"), 5L, List.of(), false, LocalDate.of(2026, 10, 6), null, null));
    }

    private static String body(String specialtyIds, String title, String customerProblem, String proposedFee,
            String draftDays, String finalDays, String referenceImageUrls) {
        StringBuilder json = new StringBuilder("{\"ownerProfileId\":5");
        append(json, "specialtyIds", specialtyIds);
        append(json, "title", title);
        append(json, "customerProblem", customerProblem);
        append(json, "proposedSolution", "\"사진 메뉴판으로 바꿉니다.\"");
        append(json, "workPlan", "\"촬영 후 편집합니다.\"");
        append(json, "proposedFee", proposedFee);
        append(json, "draftDays", draftDays);
        append(json, "finalDays", finalDays);
        append(json, "referenceImageUrls", referenceImageUrls);
        return json.append('}').toString();
    }

    private static void append(StringBuilder json, String field, String value) {
        if (value != null) {
            json.append(",\"").append(field).append("\":").append(value);
        }
    }

    @Test
    @DisplayName("내가 보낸 제안 목록은 200과 카드 필드를 반환한다")
    void returnsMyProposals() throws Exception {
        Proposal proposal = Proposal.builder().id(31L).title("메뉴판 개선 제안").likeCount(5)
                .proposedSolution(TEXT_500).status(ProposalStatus.PENDING).build();
        Owner owner = Owner.builder().id(50L).storeName("가꿈 카페").build();
        when(proposalFacade.getMyProposals(USERNAME)).thenReturn(MyProposalListResult.of(List.of(
                MyProposalResult.of(proposal, owner, List.of(SpecialtyCategoryResult.of(
                        1L, "디자인", List.of(SpecialtyResult.of(11L, "메뉴판 디자인")))), null))));

        mockMvc.perform(get("/me/proposals").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.proposals[0].jobId").doesNotExist())
                .andExpect(jsonPath("$.data.proposals[0].jobStatus").doesNotExist())
                .andExpect(jsonPath("$.data.proposals[0].proposalId").value(31))
                .andExpect(jsonPath("$.data.proposals[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.proposals[0].likeCount").value(5))
                .andExpect(jsonPath("$.data.proposals[0].specialtyCategories[0].specialties[0].name").value("메뉴판 디자인"))
                .andExpect(jsonPath("$.data.proposals[0].proposedSolution").value(TEXT_500))
                .andExpect(jsonPath("$.data.proposals[0].store.ownerProfileId").value(50))
                .andExpect(jsonPath("$.data.proposals[0].store.storeAddress").doesNotExist())
                .andExpect(jsonPath("$.data.proposals[0].store.profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.proposals[0].createdAt").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = { "AWAITING_START", "MATCHED", "CLOSED", "CANCELLED" })
    @DisplayName("내가 보낸 제안 목록은 연결 의뢰의 작업 상태를 제안 상태와 구분해 반환한다")
    void returnsMyProposalJobStatus(JobStatus jobStatus) throws Exception {
        Proposal proposal = Proposal.builder().id(31L).title("메뉴판 개선 제안").likeCount(5)
                .status(ProposalStatus.ACCEPTED).build();
        Job job = Job.builder().id(42L).proposalId(31L).status(jobStatus).build();
        when(proposalFacade.getMyProposals(USERNAME)).thenReturn(MyProposalListResult.of(List.of(
                MyProposalResult.of(proposal, Owner.builder().id(50L).build(), List.of(), job))));

        mockMvc.perform(get("/me/proposals").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposals[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.proposals[0].jobId").value(42))
                .andExpect(jsonPath("$.data.proposals[0].jobStatus").value(jobStatus.name()));
    }

    @Test
    @DisplayName("같은 제안의 생성 시각은 목록과 상세에서 같은 한국 시각으로 반환하고 UTC 오후 3시 이후는 다음 날이 된다")
    void returnsSameKoreaCreatedAtInListAndDetail() throws Exception {
        // UTC 10월 5일 15:30 = 한국 10월 6일 00:30
        Proposal proposal = Proposal.builder().id(31L).title("메뉴판 개선 제안").likeCount(0)
                .draftDays(3).finalDays(7).referenceImageUrls(List.of()).status(ProposalStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 10, 5, 15, 30)).build();
        when(proposalFacade.getMyProposals(USERNAME)).thenReturn(MyProposalListResult.of(List.of(
                MyProposalResult.of(proposal, Owner.builder().id(50L).storeName("가꿈 카페").build(),
                        List.of(), null))));
        when(proposalFacade.getProposalDetail(USERNAME, 31L))
                .thenReturn(ProposalDetailResult.of(proposal, "가꿈 카페", null, null,
                        Student.builder().id(7L).build(), User.builder().name("김학생").build(),
                        new java.math.BigDecimal("4.3"), 5L, List.of(), false, LocalDate.of(2026, 10, 6), null, null));

        mockMvc.perform(get("/me/proposals").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposals[0].createdAt").value("2026-10-06T00:30:00+09:00"));
        mockMvc.perform(get("/proposals/31").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-06T00:30:00+09:00"));
    }

    @Test
    @DisplayName("보낸 제안이 없으면 빈 배열을 반환한다")
    void returnsEmptyMyProposals() throws Exception {
        when(proposalFacade.getMyProposals(USERNAME)).thenReturn(MyProposalListResult.of(List.of()));

        mockMvc.perform(get("/me/proposals").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposals").isEmpty());
    }

    @Test
    @DisplayName("학생이 아니면 403 PROPOSAL_403_LIST_STUDENT를 반환한다")
    void rejectsNonStudentMyProposals() throws Exception {
        when(proposalFacade.getMyProposals(USERNAME))
                .thenThrow(new BusinessException(ErrorCode.PROPOSAL_LIST_STUDENT_REQUIRED));

        mockMvc.perform(get("/me/proposals").principal(authentication))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("받은 제안 목록은 200과 카드·학생 5개 필드(프로필 사진 포함, 학번은 입학연도 두 자리), 연결 의뢰 상태, 한국 시각 생성 시각을 반환한다")
    void returnsReceivedProposals() throws Exception {
        // UTC 10월 5일 15:30 = 한국 10월 6일 00:30
        Proposal proposal = Proposal.builder().id(101L).title("메뉴판 개선 제안").likeCount(12)
                .proposedSolution("사진 중심 메뉴판으로 바꿔드릴게요.").status(ProposalStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 10, 5, 15, 30)).build();
        Student student = Student.builder().id(7L).userId("student-user").major("소프트웨어학부")
                .studentNumber("2024123456").profileImageUrl("https://cdn.example.com/students/7/profile.png")
                .build();
        User studentUser = User.builder().id("student-user").name("홍길동").build();
        when(proposalFacade.getReceivedProposals(USERNAME)).thenReturn(ReceivedProposalListResult.of(List.of(
                ReceivedProposalResult.of(proposal, student, studentUser, List.of(SpecialtyCategoryResult.of(
                        1L, "디자인", List.of(SpecialtyResult.of(3L, "편집 디자인")))),
                        Job.builder().id(42L).proposalId(101L).status(JobStatus.AWAITING_START).build()))));

        mockMvc.perform(get("/me/received-proposals").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.proposals[0].jobId").value(42))
                .andExpect(jsonPath("$.data.proposals[0].jobStatus").value("AWAITING_START"))
                .andExpect(jsonPath("$.data.proposals[0].createdAt").value("2026-10-06T00:30:00+09:00"))
                .andExpect(jsonPath("$.data.proposals[0].proposalId").value(101))
                .andExpect(jsonPath("$.data.proposals[0].title").value("메뉴판 개선 제안"))
                .andExpect(jsonPath("$.data.proposals[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.proposals[0].likeCount").value(12))
                .andExpect(jsonPath("$.data.proposals[0].specialtyCategories[0].id").value(1))
                .andExpect(jsonPath("$.data.proposals[0].specialtyCategories[0].name").value("디자인"))
                .andExpect(jsonPath("$.data.proposals[0].specialtyCategories[0].specialties[0].id").value(3))
                .andExpect(jsonPath("$.data.proposals[0].specialtyCategories[0].specialties[0].name").value("편집 디자인"))
                .andExpect(jsonPath("$.data.proposals[0].proposedSolution").value("사진 중심 메뉴판으로 바꿔드릴게요."))
                .andExpect(jsonPath("$.data.proposals[0].student.studentProfileId").value(7))
                .andExpect(jsonPath("$.data.proposals[0].student.name").value("홍길동"))
                .andExpect(jsonPath("$.data.proposals[0].student.studentNumber").value("24"))
                .andExpect(jsonPath("$.data.proposals[0].student.major").value("소프트웨어학부"))
                .andExpect(jsonPath("$.data.proposals[0].student.profileImageUrl")
                        .value("https://cdn.example.com/students/7/profile.png"))
                .andExpect(jsonPath("$.data.proposals[0].student.userId").doesNotExist())
                .andExpect(jsonPath("$.data.proposals[0].student.averageRating").doesNotExist());
    }

    @Test
    @DisplayName("결제 전이라 의뢰가 없는 받은 제안은 jobId와 jobStatus를, 사진이 없는 학생은 프로필 사진을 null로 반환한다")
    void returnsNullJobFieldsForReceivedProposalWithoutJob() throws Exception {
        // UTC 10월 5일 14:59 = 한국 10월 5일 23:59. 날짜가 넘어가지 않는 경계다
        Proposal proposal = Proposal.builder().id(101L).title("메뉴판 개선 제안").likeCount(0)
                .status(ProposalStatus.PENDING).createdAt(LocalDateTime.of(2026, 10, 5, 14, 59)).build();
        when(proposalFacade.getReceivedProposals(USERNAME)).thenReturn(ReceivedProposalListResult.of(List.of(
                ReceivedProposalResult.of(proposal, Student.builder().id(7L).build(),
                        User.builder().name("홍길동").build(), List.of(), null))));

        mockMvc.perform(get("/me/received-proposals").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposals[0].jobId").value(nullValue()))
                .andExpect(jsonPath("$.data.proposals[0].jobStatus").value(nullValue()))
                .andExpect(jsonPath("$.data.proposals[0].student", hasKey("profileImageUrl")))
                .andExpect(jsonPath("$.data.proposals[0].student.profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.proposals[0].createdAt").value("2026-10-05T23:59:00+09:00"));
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = { "AWAITING_START", "MATCHED", "CLOSED", "CANCELLED" })
    @DisplayName("받은 제안 목록은 연결 의뢰의 현재 상태를 제안 상태와 구분해 반환한다")
    void returnsReceivedProposalJobStatus(JobStatus jobStatus) throws Exception {
        Proposal proposal = Proposal.builder().id(31L).title("메뉴판 개선 제안").likeCount(5)
                .status(ProposalStatus.ACCEPTED).build();
        Job job = Job.builder().id(42L).proposalId(31L).status(jobStatus).build();
        when(proposalFacade.getReceivedProposals(USERNAME)).thenReturn(ReceivedProposalListResult.of(List.of(
                ReceivedProposalResult.of(proposal, Student.builder().id(7L).build(),
                        User.builder().name("홍길동").build(), List.of(), job))));

        mockMvc.perform(get("/me/received-proposals").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposals[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.proposals[0].jobId").value(42))
                .andExpect(jsonPath("$.data.proposals[0].jobStatus").value(jobStatus.name()));
    }

    @Test
    @DisplayName("받은 제안이 없으면 빈 배열을 반환한다")
    void returnsEmptyReceivedProposals() throws Exception {
        when(proposalFacade.getReceivedProposals(USERNAME)).thenReturn(ReceivedProposalListResult.of(List.of()));

        mockMvc.perform(get("/me/received-proposals").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.proposals").isArray())
                .andExpect(jsonPath("$.data.proposals").isEmpty());
    }

    @Test
    @DisplayName("사장님이 아니면 403 PROPOSAL_403_LIST_OWNER 오류 형식을 반환한다")
    void rejectsNonOwnerReceivedProposals() throws Exception {
        when(proposalFacade.getReceivedProposals(USERNAME))
                .thenThrow(new BusinessException(ErrorCode.PROPOSAL_LIST_OWNER_REQUIRED));

        mockMvc.perform(get("/me/received-proposals").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_403_LIST_OWNER"));
    }

    @Test
    @DisplayName("의뢰서 거절은 본문 없이 인증 사용자와 의뢰 ID를 전달하고 200과 상태·전액 환불 금액·거절 시각을 반환한다")
    void declinesProposalJob() throws Exception {
        Proposal proposal = Proposal.builder().id(31L).status(ProposalStatus.REJECTED).build();
        Job job = Job.builder().id(42L).status(JobStatus.CANCELLED)
                .completedAt(LocalDateTime.of(2026, 10, 6, 12, 0)).build();
        when(proposalFacade.declineProposalJob(USERNAME, 42L)).thenReturn(ProposalJobDeclineResult.of(job, proposal,
                new RefundedPaymentData(91L, 100_000L, 0L, 100_000L, Instant.parse("2026-10-06T03:00:00Z"))));

        mockMvc.perform(post("/jobs/42/decline").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data.jobId").value(42))
                .andExpect(jsonPath("$.data.jobStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.proposalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.paidAmount").value(100000))
                .andExpect(jsonPath("$.data.studentCompensationAmount").value(0))
                .andExpect(jsonPath("$.data.refundAmount").value(100000))
                .andExpect(jsonPath("$.data.declinedAt").value("2026-10-06T21:00:00+09:00"));

        verify(proposalFacade).declineProposalJob(USERNAME, 42L);
    }

    @ParameterizedTest(name = "의뢰 ID {0}")
    @ValueSource(strings = { "abc", "0", "-1", "1.5" })
    @DisplayName("의뢰서 거절은 숫자가 아니거나 0 이하인 의뢰 ID를 COMMON_400으로 거부하고 파사드를 호출하지 않는다")
    void rejectsInvalidJobIdForDecline(String jobId) throws Exception {
        mockMvc.perform(post("/jobs/" + jobId + "/decline").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(proposalFacade);
    }

    static Stream<Arguments> declineErrors() {
        return Stream.of(
                Arguments.of(ErrorCode.UNAUTHORIZED, 401, "COMMON_401", null),
                Arguments.of(ErrorCode.JOB_DECLINE_FORBIDDEN, 403, "JOB_DECLINE_403", "제안한 학생만 의뢰서를 거절할 수 있습니다."),
                Arguments.of(ErrorCode.JOB_NOT_FOUND, 404, "JOB_404", null),
                Arguments.of(ErrorCode.JOB_DECLINE_NOT_AVAILABLE, 409, "JOB_DECLINE_409", "거절할 수 없는 의뢰 상태입니다."),
                Arguments.of(ErrorCode.INTERNAL_SERVER_ERROR, 500, "COMMON_500", null));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("declineErrors")
    @DisplayName("의뢰서 거절의 잠긴 사용자·권한·대상·상태·데이터 오류는 각 오류 코드의 상태와 기존 오류 응답 형식으로 반환한다")
    void returnsDeclineErrors(ErrorCode errorCode, int status, String code, String message) throws Exception {
        when(proposalFacade.declineProposalJob(USERNAME, 42L)).thenThrow(new BusinessException(errorCode));

        org.springframework.test.web.servlet.ResultActions result =
                mockMvc.perform(post("/jobs/42/decline").principal(authentication))
                        .andExpect(status().is(status))
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value(code));
        if (message != null) {
            result.andExpect(jsonPath("$.error.message").value(message));
        }
    }
}
