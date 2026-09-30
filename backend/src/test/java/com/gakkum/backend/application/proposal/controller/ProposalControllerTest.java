package com.gakkum.backend.application.proposal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.proposal.facade.ProposalFacade;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCreateResult;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("학생 제안 전송 컨트롤러 (POST /proposals)")
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
}
