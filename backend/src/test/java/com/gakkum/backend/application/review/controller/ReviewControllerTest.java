package com.gakkum.backend.application.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.review.facade.ReviewFacade;
import com.gakkum.backend.domain.review.dto.ReviewCommandDto.CreateReviewCommand;
import com.gakkum.backend.domain.review.dto.ReviewQueryDto.ReviewCreateResult;
import com.gakkum.backend.domain.review.dto.ReviewQueryDto.StudentReviewResult;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("리뷰 작성 컨트롤러 (POST /jobs/{jobId}/reviews)")
class ReviewControllerTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String URL = "/jobs/42/reviews";

    private final ReviewFacade reviewFacade = mock(ReviewFacade.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("정상 요청은 201과 리뷰 ID, 의뢰 ID를 반환하고 내용 앞뒤 공백을 제거해 넘긴다")
    void returnsCreatedReview() throws Exception {
        givenCreated();

        mockMvc.perform(post(URL).principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positivePoints\":[\"QUALITY_OUTPUT\",\"KINDNESS\"],"
                                + "\"content\":\"  꼼꼼하게 작업해 주셨어요.  \",\"rating\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reviewId").value(301))
                .andExpect(jsonPath("$.data.jobId").value(42))
                .andExpect(jsonPath("$.error").doesNotExist());

        CreateReviewCommand command = captureCommand();
        assertThat(command.getUsername()).isEqualTo(USERNAME);
        assertThat(command.getJobId()).isEqualTo(42L);
        assertThat(command.getPositivePoints())
                .containsExactly(ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.KINDNESS);
        assertThat(command.getContent()).isEqualTo("꼼꼼하게 작업해 주셨어요.");
        assertThat(command.getRating()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"content\":\"좋았어요\",\"rating\":1}",
            "{\"positivePoints\":null,\"content\":\"좋았어요\",\"rating\":1}"})
    @DisplayName("좋은 점을 생략하거나 null로 보내면 빈 목록으로 넘긴다")
    void treatsMissingPositivePointsAsEmpty(String body) throws Exception {
        givenCreated();

        mockMvc.perform(post(URL).principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        assertThat(captureCommand().getPositivePoints()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"positivePoints\":[\"KINDNESS\"],\"rating\":5}",
            "{\"positivePoints\":[\"KINDNESS\"],\"content\":null,\"rating\":5}",
            "{\"positivePoints\":[\"KINDNESS\"],\"content\":\"\",\"rating\":5}",
            "{\"positivePoints\":[\"KINDNESS\"],\"content\":\"   \",\"rating\":5}",
            "{\"content\":\" \\n\\t \",\"rating\":5}",
            "{\"rating\":5}"})
    @DisplayName("내용을 생략하거나 null·빈 문자열·공백만 보내면 201을 반환하고 내용을 null로 넘긴다")
    void treatsMissingContentAsNull(String body) throws Exception {
        givenCreated();

        mockMvc.perform(post(URL).principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reviewId").value(301))
                .andExpect(jsonPath("$.data.jobId").value(42));

        CreateReviewCommand command = captureCommand();
        assertThat(command.getContent()).isNull();
        assertThat(command.getRating()).isEqualTo(5);
    }

    @Test
    @DisplayName("좋은 점 다섯 개를 모두 고를 수 있다")
    void acceptsAllPositivePoints() throws Exception {
        givenCreated();

        mockMvc.perform(post(URL).principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positivePoints\":[\"QUALITY_OUTPUT\",\"ON_TIME_DELIVERY\",\"FAST_COMMUNICATION\","
                                + "\"KINDNESS\",\"REVISION_FEEDBACK\"],\"content\":\"최고예요\",\"rating\":5}"))
                .andExpect(status().isCreated());

        assertThat(captureCommand().getPositivePoints()).containsExactly(ReviewPositivePoint.values());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"content\":\"좋았어요\"}",
            "{\"positivePoints\":[\"KINDNESS\"]}",
            "{\"rating\":null}",
            "{\"content\":\"좋았어요\",\"rating\":0}",
            "{\"content\":\"좋았어요\",\"rating\":6}",
            "{\"content\":\"좋았어요\",\"rating\":\"다섯\"}",
            "{\"content\":\"좋았어요\",\"rating\":3.5}",
            "{\"positivePoints\":[\"GOOD\"],\"content\":\"좋았어요\",\"rating\":5}",
            "{\"positivePoints\":[\"KINDNESS\",\"KINDNESS\"],\"content\":\"좋았어요\",\"rating\":5}",
            "{\"positivePoints\":[null],\"content\":\"좋았어요\",\"rating\":5}",
            "{\"positivePoints\":\"KINDNESS\",\"content\":\"좋았어요\",\"rating\":5}"})
    @DisplayName("별점 누락, 별점 범위 초과, 알 수 없거나 중복된 좋은 점 코드는 COMMON_400으로 거부한다")
    void rejectsInvalidRequest(String body) throws Exception {
        mockMvc.perform(post(URL).principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(reviewFacade);
    }

    @Test
    @DisplayName("리뷰 내용이 5,000자를 넘으면 COMMON_400으로 거부하고 5,000자는 허용한다")
    void limitsContentLength() throws Exception {
        mockMvc.perform(post(URL).principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "가".repeat(5001) + "\",\"rating\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(reviewFacade);

        givenCreated();
        mockMvc.perform(post(URL).principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "가".repeat(5000) + "\",\"rating\":3}"))
                .andExpect(status().isCreated());
        assertThat(captureCommand().getContent()).hasSize(5000);
    }

    @Test
    @DisplayName("의뢰 ID가 0 이하이면 COMMON_400으로 거부한다")
    void rejectsNonPositiveJobId() throws Exception {
        mockMvc.perform(post("/jobs/0/reviews").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"좋았어요\",\"rating\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(reviewFacade);
    }

    @Test
    @DisplayName("받은 리뷰 조회는 200과 제출물 ID, 의뢰 제목, 매장 이름, 별점, 작성일, 좋은 점 코드, 내용을 반환한다")
    void returnsStudentReview() throws Exception {
        when(reviewFacade.getStudentReview(USERNAME, 42L)).thenReturn(StudentReviewResult.of(
                Review.builder()
                        .jobId(42L)
                        .rating(5)
                        .createdAt(LocalDateTime.of(2026, 9, 28, 21, 30, 15))
                        .positivePoints(List.of(ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.KINDNESS))
                        .content("꼼꼼하게 작업해 주셨어요.")
                        .build(),
                82L, "가을 메뉴 포스터 디자인", "가꿈 베이커리"));

        mockMvc.perform(get("/jobs/42/review").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.submissionId").value(82))
                .andExpect(jsonPath("$.data.jobTitle").value("가을 메뉴 포스터 디자인"))
                .andExpect(jsonPath("$.data.storeName").value("가꿈 베이커리"))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-28"))
                .andExpect(jsonPath("$.data.positivePoints[0]").value("QUALITY_OUTPUT"))
                .andExpect(jsonPath("$.data.positivePoints[1]").value("KINDNESS"))
                .andExpect(jsonPath("$.data.content").value("꼼꼼하게 작업해 주셨어요."))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("글 없는 리뷰를 조회하면 내용 키를 빼지 않고 null로 반환한다")
    void returnsStudentReviewWithoutContent() throws Exception {
        when(reviewFacade.getStudentReview(USERNAME, 42L)).thenReturn(StudentReviewResult.of(
                Review.builder()
                        .jobId(42L)
                        .rating(4)
                        .createdAt(LocalDateTime.of(2026, 9, 28, 21, 30, 15))
                        .positivePoints(List.of(ReviewPositivePoint.KINDNESS))
                        .build(),
                82L, "가을 메뉴 포스터 디자인", "가꿈 베이커리"));

        mockMvc.perform(get("/jobs/42/review").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("content")))
                .andExpect(jsonPath("$.data.content").value(nullValue()))
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.positivePoints[0]").value("KINDNESS"));
    }

    @Test
    @DisplayName("받은 리뷰 조회에서 의뢰 ID가 0 이하이면 COMMON_400으로 거부한다")
    void rejectsNonPositiveJobIdOnLookup() throws Exception {
        mockMvc.perform(get("/jobs/0/review").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(reviewFacade);
    }

    private void givenCreated() {
        when(reviewFacade.createReview(any())).thenReturn(ReviewCreateResult.from(
                Review.builder().id(301L).jobId(42L).build()));
    }

    private CreateReviewCommand captureCommand() {
        ArgumentCaptor<CreateReviewCommand> captor = ArgumentCaptor.forClass(CreateReviewCommand.class);
        verify(reviewFacade).createReview(captor.capture());
        return captor.getValue();
    }
}
