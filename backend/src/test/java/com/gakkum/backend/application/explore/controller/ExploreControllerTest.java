package com.gakkum.backend.application.explore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.explore.dto.ExploreCommandDto.ExploreCommand;
import com.gakkum.backend.application.explore.dto.ExploreCursor;
import com.gakkum.backend.application.explore.dto.ExploreItemType;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ExploreItemResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ExploreResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.JobCardResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ProposalCardResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.SpecialtyResult;
import com.gakkum.backend.application.explore.dto.ExploreSort;
import com.gakkum.backend.application.explore.dto.ExploreType;
import com.gakkum.backend.application.explore.facade.ExploreFacade;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("탐색 컨트롤러 (GET /explore)")
class ExploreControllerTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 30, 10, 0);

    private final ExploreFacade exploreFacade = mock(ExploreFacade.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExploreController(exploreFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("파라미터가 없으면 전체 종류·최신순·20개·첫 페이지로 조회한다")
    void usesDefaults() throws Exception {
        when(exploreFacade.explore(any())).thenReturn(ExploreResult.of(List.of(), null));

        mockMvc.perform(get("/explore").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        ExploreCommand command = captureCommand();
        assertThat(command.getUsername()).isEqualTo(USERNAME);
        assertThat(command.getSpecialtyCategoryId()).isNull();
        assertThat(command.getType()).isEqualTo(ExploreType.ALL);
        assertThat(command.getSort()).isEqualTo(ExploreSort.LATEST);
        assertThat(command.getSize()).isEqualTo(20);
        assertThat(command.getCursor()).isNull();
    }

    @Test
    @DisplayName("같은 필터로 만든 커서와 분류·종류·정렬·크기를 해석해 파사드에 넘긴다")
    void passesFiltersAndCursor() throws Exception {
        when(exploreFacade.explore(any())).thenReturn(ExploreResult.of(List.of(), null));
        String cursor = ExploreCursor.of(ExploreSort.LIKES, ExploreType.PROPOSAL, 3L,
                ExploreItemType.PROPOSAL, 5, CREATED_AT, 31L).encode();

        mockMvc.perform(get("/explore").principal(authentication)
                        .param("specialtyCategoryId", "3")
                        .param("type", "PROPOSAL")
                        .param("sort", "LIKES")
                        .param("size", "100")
                        .param("cursor", cursor))
                .andExpect(status().isOk());

        ExploreCommand command = captureCommand();
        assertThat(command.getSpecialtyCategoryId()).isEqualTo(3L);
        assertThat(command.getSize()).isEqualTo(100);
        assertThat(command.getCursor().getLikeCount()).isEqualTo(5);
        assertThat(command.getCursor().getId()).isEqualTo(31L);
    }

    @Test
    @DisplayName("응답은 한 배열에 제안·의뢰 카드를 type과 함께 담고 다음 커서를 반환한다")
    void returnsMixedCards() throws Exception {
        List<SpecialtyCategoryResult> categories = List.of(
                SpecialtyCategoryResult.of(1L, "디자인", List.of(SpecialtyResult.of(3L, "로고 디자인"))),
                SpecialtyCategoryResult.of(2L, "영상", List.of(SpecialtyResult.of(11L, "숏폼 촬영"))));
        Proposal proposal = Proposal.builder().id(31L).title("메뉴판 개선 제안").likeCount(4)
                .status(ProposalStatus.AWAITING_START).proposedSolution("사진 메뉴판으로 바꿉니다.").build();
        Job job = Job.builder().id(42L).title("로고 제작").status(JobStatus.MATCHED).budget(300_000L)
                .draftDeadline(LocalDate.of(2026, 10, 10)).finalDeadline(LocalDate.of(2026, 10, 20)).build();
        when(exploreFacade.explore(any())).thenReturn(ExploreResult.of(List.of(
                ProposalCardResult.of(proposal, "가꿈 분식", "김학생", true, categories),
                JobCardResult.of(job, JobProgressStage.DRAFT, "가꿈 카페", categories.subList(0, 1), null)), "next"));

        mockMvc.perform(get("/explore").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].type").value("PROPOSAL"))
                .andExpect(jsonPath("$.data.items[0].proposalId").value(31))
                .andExpect(jsonPath("$.data.items[0].title").value("메뉴판 개선 제안"))
                .andExpect(jsonPath("$.data.items[0].storeName").value("가꿈 분식"))
                .andExpect(jsonPath("$.data.items[0].likeCount").value(4))
                .andExpect(jsonPath("$.data.items[0].studentName").value("김학생"))
                .andExpect(jsonPath("$.data.items[0].status").value("AWAITING_START"))
                .andExpect(jsonPath("$.data.items[0].proposedSolution").value("사진 메뉴판으로 바꿉니다."))
                .andExpect(jsonPath("$.data.items[0].likedByMe").value(true))
                .andExpect(jsonPath("$.data.items[0].specialtyCategories.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].specialtyCategories[1].specialties[0].name").value("숏폼 촬영"))
                .andExpect(jsonPath("$.data.items[0].jobId").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].type").value("JOB"))
                .andExpect(jsonPath("$.data.items[1].jobId").value(42))
                .andExpect(jsonPath("$.data.items[1].storeName").value("가꿈 카페"))
                .andExpect(jsonPath("$.data.items[1].title").value("로고 제작"))
                .andExpect(jsonPath("$.data.items[1].progressStage").value("DRAFT"))
                .andExpect(jsonPath("$.data.items[1].status").value("MATCHED"))
                .andExpect(jsonPath("$.data.items[1].draftDeadline").exists())
                .andExpect(jsonPath("$.data.items[1].finalDeadline").exists())
                .andExpect(jsonPath("$.data.items[1].budget").value(300000))
                .andExpect(jsonPath("$.data.items[1].applied").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].budget").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].applied").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].specialtyCategories[0].id").value(1))
                .andExpect(jsonPath("$.data.items[1].likeCount").doesNotExist())
                .andExpect(jsonPath("$.data.items[1]", not(hasKey("studentName"))))
                .andExpect(jsonPath("$.data.items[1]", not(hasKey("proposedSolution"))))
                .andExpect(jsonPath("$.data.items[1]", not(hasKey("likedByMe"))))
                .andExpect(jsonPath("$.data.nextCursor").value("next"))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    @DisplayName("의뢰 카드는 지원서 상태를 Boolean이 아닌 문자열 그대로 담고, 지원 상태가 없는 카드는 applied 키 자체를 내리지 않는다")
    void returnsApplicationStatusAsStringAndOmitsMissing() throws Exception {
        Job job = Job.builder().id(42L).title("로고 제작").status(JobStatus.OPEN).budget(300_000L).build();
        when(exploreFacade.explore(any())).thenReturn(ExploreResult.of(List.of(
                JobCardResult.of(job, JobProgressStage.REQUESTED, "가꿈 카페", List.of(), JobApplicationStatus.PENDING),
                JobCardResult.of(job, JobProgressStage.REQUESTED, "가꿈 카페", List.of(), JobApplicationStatus.ACCEPTED),
                JobCardResult.of(job, JobProgressStage.REQUESTED, "가꿈 카페", List.of(), JobApplicationStatus.REJECTED),
                JobCardResult.of(job, JobProgressStage.REQUESTED, "가꿈 카페", List.of(), null)), null));

        mockMvc.perform(get("/explore").principal(authentication).param("type", "JOB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].applied").isString())
                .andExpect(jsonPath("$.data.items[0].applied").value("PENDING"))
                .andExpect(jsonPath("$.data.items[1].applied").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.items[2].applied").value("REJECTED"))
                // 공고 상태와 본인 지원 상태는 별개로 내린다
                .andExpect(jsonPath("$.data.items[2].status").value("OPEN"))
                .andExpect(jsonPath("$.data.items[3]", not(hasKey("applied"))))
                .andExpect(jsonPath("$.data.items[3].budget").value(300000));
    }

    @Test
    @DisplayName("제안 카드는 공감하지 않았어도 likedByMe 키를 false로 내리고 네 가지 상태를 문자열 그대로 담는다")
    void returnsLikedByMeFalseAndEveryProposalStatus() throws Exception {
        when(exploreFacade.explore(any())).thenReturn(ExploreResult.of(Stream.of(ProposalStatus.values())
                .map(status -> (ExploreItemResult) ProposalCardResult.of(
                        Proposal.builder().id(31L).title("메뉴판 개선 제안").likeCount(7).status(status)
                                .proposedSolution("해결 방안").build(),
                        "가꿈 분식", "김학생", false, List.of()))
                .toList(), null));

        mockMvc.perform(get("/explore").principal(authentication).param("type", "PROPOSAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0]", hasKey("likedByMe")))
                .andExpect(jsonPath("$.data.items[0].likedByMe").value(false))
                .andExpect(jsonPath("$.data.items[0].likeCount").value(7))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[1].status").value("AWAITING_START"))
                .andExpect(jsonPath("$.data.items[2].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.items[3].status").value("REJECTED"));
    }

    static Stream<Arguments> invalidRequests() {
        String otherFilterCursor = ExploreCursor.of(ExploreSort.LATEST, ExploreType.ALL, 3L,
                ExploreItemType.JOB, null, CREATED_AT, 1L).encode();
        return Stream.of(
                Arguments.of("크기 0", get("/explore").param("size", "0")),
                Arguments.of("크기 101", get("/explore").param("size", "101")),
                Arguments.of("숫자가 아닌 크기", get("/explore").param("size", "many")),
                Arguments.of("분류 ID 0", get("/explore").param("specialtyCategoryId", "0")),
                Arguments.of("음수 분류 ID", get("/explore").param("specialtyCategoryId", "-1")),
                Arguments.of("숫자가 아닌 분류 ID", get("/explore").param("specialtyCategoryId", "design")),
                Arguments.of("없는 종류", get("/explore").param("type", "REVIEW")),
                Arguments.of("없는 정렬", get("/explore").param("sort", "POPULAR")),
                Arguments.of("전체 종류 좋아요순", get("/explore").param("sort", "LIKES")),
                Arguments.of("의뢰 좋아요순", get("/explore").param("type", "JOB").param("sort", "LIKES")),
                Arguments.of("해석할 수 없는 커서", get("/explore").param("cursor", "abc")),
                Arguments.of("다른 분류의 커서", get("/explore").param("specialtyCategoryId", "4")
                        .param("cursor", otherFilterCursor)),
                Arguments.of("다른 정렬의 커서", get("/explore").param("specialtyCategoryId", "3")
                        .param("sort", "OLDEST").param("cursor", otherFilterCursor)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("잘못된 파라미터·정렬 조합·커서는 COMMON_400으로 거부하고 파사드를 호출하지 않는다")
    void rejectsInvalidRequest(String caseName, MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(exploreFacade);
    }

    private ExploreCommand captureCommand() {
        ArgumentCaptor<ExploreCommand> captor = ArgumentCaptor.forClass(ExploreCommand.class);
        verify(exploreFacade).explore(captor.capture());
        return captor.getValue();
    }
}
