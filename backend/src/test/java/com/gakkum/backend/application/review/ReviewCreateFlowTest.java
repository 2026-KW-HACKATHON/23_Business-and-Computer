package com.gakkum.backend.application.review;

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

import java.time.Clock;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.review.controller.ReviewController;
import com.gakkum.backend.application.review.facade.ReviewFacade;
import com.gakkum.backend.domain.job.entity.Job;
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
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;
import com.gakkum.backend.domain.review.repository.ReviewRepository;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("사장님 → 학생 리뷰 작성 전체 흐름 (POST /jobs/{jobId}/reviews)")
class ReviewCreateFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String URL = "/jobs/42/reviews";
    private static final String BODY =
            "{\"positivePoints\":[\"FAST_COMMUNICATION\"],\"content\":\"소통이 빨라 좋았어요.\",\"rating\":5}";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JobService jobService = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
                mock(JobApplicationRepository.class), mock(JobSubmissionRepository.class), Clock.systemUTC());
        ReviewFacade facade = new ReviewFacade(new UserService(userRepository, mock(JwtService.class)),
                new OwnerService(ownerRepository), new StudentService(mock(StudentRepository.class)), jobService,
                new ReviewService(reviewRepository));
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("본인의 완료된 의뢰에 리뷰를 쓰면 201을 반환하고 의뢰의 담당 학생을 리뷰 대상으로 저장한다")
    void createsReviewForSelectedStudent() throws Exception {
        givenActiveUser(UserRole.OWNER);
        givenOwnedJob(JobStatus.CLOSED);
        when(reviewRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            return Review.builder().id(301L).jobId(review.getJobId()).build();
        });

        perform()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reviewId").value(301))
                .andExpect(jsonPath("$.data.jobId").value(42));

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getJobId()).isEqualTo(42L);
        assertThat(captor.getValue().getOwnerProfileId()).isEqualTo(5L);
        assertThat(captor.getValue().getStudentProfileId()).isEqualTo(7L);
        assertThat(captor.getValue().getPositivePoints()).containsExactly(ReviewPositivePoint.FAST_COMMUNICATION);
        assertThat(captor.getValue().getContent()).isEqualTo("소통이 빨라 좋았어요.");
        assertThat(captor.getValue().getRating()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"positivePoints\":[\"FAST_COMMUNICATION\"],\"rating\":4}",
            "{\"positivePoints\":[\"FAST_COMMUNICATION\"],\"content\":null,\"rating\":4}",
            "{\"positivePoints\":[\"FAST_COMMUNICATION\"],\"content\":\"\",\"rating\":4}",
            "{\"positivePoints\":[\"FAST_COMMUNICATION\"],\"content\":\"   \",\"rating\":4}"})
    @DisplayName("글 없이 별점과 좋은 점만 보내면 201을 반환하고 내용이 null인 리뷰를 저장한다")
    void createsReviewWithoutContent(String body) throws Exception {
        givenActiveUser(UserRole.OWNER);
        givenOwnedJob(JobStatus.CLOSED);
        when(reviewRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            return Review.builder().id(301L).jobId(review.getJobId()).build();
        });

        perform(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reviewId").value(301))
                .andExpect(jsonPath("$.data.jobId").value(42));

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContent()).isNull();
        assertThat(captor.getValue().getStudentProfileId()).isEqualTo(7L);
        assertThat(captor.getValue().getPositivePoints()).containsExactly(ReviewPositivePoint.FAST_COMMUNICATION);
        assertThat(captor.getValue().getRating()).isEqualTo(4);
    }

    @Test
    @DisplayName("다른 사장님의 의뢰이거나 없는 의뢰면 404 JOB_404를 반환하고 저장하지 않는다")
    void rejectsOtherOwnersJob() throws Exception {
        givenActiveUser(UserRole.OWNER);
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.empty());

        perform()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_404"));
        verifyNoInteractions(reviewRepository);
    }

    @Test
    @DisplayName("진행 중인 의뢰에 리뷰를 쓰면 409 REVIEW_409_STATUS를 반환한다")
    void rejectsMatchedJob() throws Exception {
        givenActiveUser(UserRole.OWNER);
        givenOwnedJob(JobStatus.MATCHED);

        perform()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVIEW_409_STATUS"));
        verifyNoInteractions(reviewRepository);
    }

    @Test
    @DisplayName("이미 리뷰를 쓴 의뢰면 409 REVIEW_409_DUPLICATE를 반환한다")
    void rejectsDuplicateReview() throws Exception {
        givenActiveUser(UserRole.OWNER);
        givenOwnedJob(JobStatus.CLOSED);
        when(reviewRepository.existsByJobId(42L)).thenReturn(true);

        perform()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVIEW_409_DUPLICATE"));
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("사장님 프로필이 없는 사용자(학생 포함)는 403 OWNER_403으로 거부하고 의뢰를 조회하지 않는다")
    void rejectsUserWithoutOwnerProfile() throws Exception {
        givenActiveUser(UserRole.STUDENT);
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.empty());

        perform()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OWNER_403"));
        verifyNoInteractions(jobRepository, reviewRepository);
    }

    @Test
    @DisplayName("잠긴 사용자는 401 COMMON_401로 거부한다")
    void rejectsInactiveUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        perform()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(jobRepository, reviewRepository);
    }

    private ResultActions perform() throws Exception {
        return perform(BODY);
    }

    private ResultActions perform(String body) throws Exception {
        return mockMvc.perform(post(URL).principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void givenActiveUser(UserRole role) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(role).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.of(Owner.builder().id(5L).build()));
    }

    private void givenOwnedJob(JobStatus status) {
        when(jobRepository.findByIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .status(status)
                .selectedStudentProfileId(7L)
                .build()));
    }
}
