package com.gakkum.backend.application.review;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.review.controller.ReviewController;
import com.gakkum.backend.application.review.facade.ReviewFacade;
import com.gakkum.backend.domain.job.entity.Job;
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
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;
import com.gakkum.backend.domain.review.repository.ReviewRepository;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("의뢰 리뷰 단건 조회 전체 흐름 (GET /jobs/{jobId}/review)")
class StudentReviewFlowTest {

    private static final String USERNAME = "KAKAO_67890";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5E";
    private static final String URL = "/jobs/42/review";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JobService jobService = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
                mock(JobApplicationRepository.class), jobSubmissionRepository, Clock.systemUTC());
        ReviewFacade facade = new ReviewFacade(new UserService(userRepository, mock(JwtService.class)),
                new OwnerService(ownerRepository), new StudentService(studentRepository), jobService,
                new ReviewService(reviewRepository), mock(ApplicationEventPublisher.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("담당 학생은 7개 필드를 받고, 수정안이 최종 승인된 의뢰면 초안이 아닌 수정안 ID를 받는다")
    void returnsReviewWithApprovedRevisionId() throws Exception {
        givenActiveStudent(UserRole.STUDENT, 7L);
        givenReview(7L);
        givenReviewedJob();
        givenStoreName("가꿈 베이커리");

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.submissionId").value(82))
                .andExpect(jsonPath("$.data.jobTitle").value("가을 메뉴 포스터 디자인"))
                .andExpect(jsonPath("$.data.storeName").value("가꿈 베이커리"))
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-28"))
                .andExpect(jsonPath("$.data.positivePoints.length()").value(2))
                .andExpect(jsonPath("$.data.positivePoints[0]").value("REVISION_FEEDBACK"))
                .andExpect(jsonPath("$.data.positivePoints[1]").value("FAST_COMMUNICATION"))
                .andExpect(jsonPath("$.data.content").value("수정 요청을 빠르게 반영해 주셨어요."))
                .andExpect(jsonPath("$.data.length()").value(7));
    }

    @Test
    @DisplayName("글 없는 리뷰도 404가 아니라 7개 필드로 조회되고 내용은 null이다")
    void returnsReviewWithoutContent() throws Exception {
        givenActiveStudent(UserRole.STUDENT, 7L);
        givenReview(7L, null);
        givenReviewedJob();
        givenStoreName("가꿈 베이커리");

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("content")))
                .andExpect(jsonPath("$.data.content").value(nullValue()))
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.positivePoints.length()").value(2))
                .andExpect(jsonPath("$.data.length()").value(7));
    }

    @Test
    @DisplayName("사장님이 매장 이름을 바꾼 뒤 조회하면 리뷰 작성 당시가 아닌 현재 매장 이름을 반환한다")
    void returnsCurrentStoreName() throws Exception {
        givenActiveStudent(UserRole.STUDENT, 7L);
        givenReview(7L);
        givenReviewedJob();

        givenStoreName("가꿈 베이커리");
        perform().andExpect(jsonPath("$.data.storeName").value("가꿈 베이커리"));

        givenStoreName("가꿈 베이커리 2호점");
        perform().andExpect(jsonPath("$.data.storeName").value("가꿈 베이커리 2호점"));
    }

    @Test
    @DisplayName("리뷰를 작성한 사장님은 담당 학생과 같은 7개 필드를 받는다")
    void returnsReviewToWritingOwner() throws Exception {
        givenActiveOwner(5L);
        givenOwnerReview(5L);
        givenReviewedJob();
        givenStoreName("가꿈 베이커리");

        perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.submissionId").value(82))
                .andExpect(jsonPath("$.data.jobTitle").value("가을 메뉴 포스터 디자인"))
                .andExpect(jsonPath("$.data.storeName").value("가꿈 베이커리"))
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-28"))
                .andExpect(jsonPath("$.data.positivePoints.length()").value(2))
                .andExpect(jsonPath("$.data.positivePoints[0]").value("REVISION_FEEDBACK"))
                .andExpect(jsonPath("$.data.positivePoints[1]").value("FAST_COMMUNICATION"))
                .andExpect(jsonPath("$.data.content").value("수정 요청을 빠르게 반영해 주셨어요."))
                .andExpect(jsonPath("$.data.length()").value(7));
        verifyNoInteractions(studentRepository);
        verify(reviewRepository, never()).findByJobIdAndStudentProfileId(any(), any());
    }

    @Test
    @DisplayName("다른 사장님이 조회하거나 리뷰가 없으면 404 REVIEW_404를 반환하고 의뢰와 제출물을 조회하지 않는다")
    void rejectsOtherOwnerOrMissingReview() throws Exception {
        givenActiveOwner(6L);
        when(reviewRepository.findByJobIdAndOwnerProfileId(42L, 6L)).thenReturn(Optional.empty());

        perform()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REVIEW_404"));
        verifyNoInteractions(jobRepository, jobSubmissionRepository, studentRepository);
        verify(ownerRepository, never()).findById(any());
    }

    @Test
    @DisplayName("사장님 프로필이 없는 사장님 사용자는 403 OWNER_403을 반환하고 리뷰를 조회하지 않는다")
    void rejectsOwnerWithoutProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.empty());

        perform()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OWNER_403"));
        verifyNoInteractions(reviewRepository, jobRepository);
    }

    @Test
    @DisplayName("역할을 정하지 않은 사용자는 403 REVIEW_403_STUDENT를 반환하고 리뷰를 조회하지 않는다")
    void rejectsPendingUser() throws Exception {
        givenActiveStudent(UserRole.PENDING, 7L);

        perform()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REVIEW_403_STUDENT"));
        verifyNoInteractions(studentRepository, ownerRepository, reviewRepository, jobRepository);
    }

    @Test
    @DisplayName("학생 프로필이 없는 학생 사용자는 403 REVIEW_403_STUDENT를 반환한다")
    void rejectsStudentWithoutProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.empty());

        perform()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REVIEW_403_STUDENT"));
        verifyNoInteractions(reviewRepository);
    }

    @Test
    @DisplayName("담당 학생이 아니거나 리뷰가 없거나 의뢰가 없으면 모두 404 REVIEW_404를 반환한다")
    void rejectsOtherStudentOrMissingReview() throws Exception {
        givenActiveStudent(UserRole.STUDENT, 8L);
        when(reviewRepository.findByJobIdAndStudentProfileId(42L, 8L)).thenReturn(Optional.empty());

        perform()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REVIEW_404"));
        verifyNoInteractions(jobRepository, jobSubmissionRepository, ownerRepository);
    }

    @Test
    @DisplayName("잠긴 사용자는 401 COMMON_401로 거부한다")
    void rejectsInactiveUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        perform()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(reviewRepository);
    }

    private ResultActions perform() throws Exception {
        return mockMvc.perform(get(URL).principal(authentication));
    }

    private void givenActiveStudent(UserRole role, Long studentProfileId) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(role).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(
                Student.builder().id(studentProfileId).userId(STUDENT_USER_ID).build()));
    }

    private void givenActiveOwner(Long ownerProfileId) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build()));
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.of(
                Owner.builder().id(ownerProfileId).userId(OWNER_USER_ID).build()));
    }

    private void givenOwnerReview(Long ownerProfileId) {
        when(reviewRepository.findByJobIdAndOwnerProfileId(42L, ownerProfileId)).thenReturn(Optional.of(
                review(7L, "수정 요청을 빠르게 반영해 주셨어요.")));
    }

    private void givenReview(Long studentProfileId) {
        givenReview(studentProfileId, "수정 요청을 빠르게 반영해 주셨어요.");
    }

    private void givenReview(Long studentProfileId, String content) {
        when(reviewRepository.findByJobIdAndStudentProfileId(42L, studentProfileId)).thenReturn(Optional.of(
                review(studentProfileId, content)));
    }

    private static Review review(Long studentProfileId, String content) {
        return Review.builder()
                .id(301L)
                .jobId(42L)
                .ownerProfileId(5L)
                .studentProfileId(studentProfileId)
                .positivePoints(List.of(
                        ReviewPositivePoint.REVISION_FEEDBACK, ReviewPositivePoint.FAST_COMMUNICATION))
                .content(content)
                .rating(4)
                .createdAt(LocalDateTime.of(2026, 9, 28, 21, 30, 15))
                .build();
    }

    // 초안(81)은 수정 요청을 받았고 수정안(82)이 최종 승인된 의뢰
    private void givenReviewedJob() {
        when(jobRepository.findById(42L)).thenReturn(Optional.of(Job.builder()
                .id(42L)
                .ownerProfileId(5L)
                .title("가을 메뉴 포스터 디자인")
                .status(JobStatus.CLOSED)
                .selectedStudentProfileId(7L)
                .build()));
        when(jobSubmissionRepository.findByJobIdAndReviewStatus(42L, JobSubmissionReviewStatus.APPROVED))
                .thenReturn(Optional.of(JobSubmission.builder()
                        .id(82L)
                        .jobId(42L)
                        .submissionType(JobSubmissionType.REVISION)
                        .revisionNumber(1)
                        .reviewStatus(JobSubmissionReviewStatus.APPROVED)
                        .build()));
    }

    private void givenStoreName(String storeName) {
        when(ownerRepository.findById(5L)).thenReturn(Optional.of(Owner.builder().id(5L).storeName(storeName).build()));
    }
}
