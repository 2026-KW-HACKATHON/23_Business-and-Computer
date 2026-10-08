package com.gakkum.backend.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.gakkum.backend.domain.review.dto.ReviewCommandDto.CreateReviewCommand;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;
import com.gakkum.backend.domain.review.repository.ReviewRepository;
import com.gakkum.backend.domain.review.repository.ReviewRepository.StudentAverageRating;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class ReviewServiceTest {

    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final ReviewService reviewService = new ReviewService(reviewRepository);

    @Test
    @DisplayName("의뢰·사장님·학생 프로필 ID와 좋은 점, 내용, 별점을 저장한다")
    void savesReview() {
        when(reviewRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Review review = reviewService.createReview(command(List.of(ReviewPositivePoint.ON_TIME_DELIVERY)), 5L, 7L);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).saveAndFlush(captor.capture());
        assertThat(review).isSameAs(captor.getValue());
        assertThat(review.getJobId()).isEqualTo(42L);
        assertThat(review.getOwnerProfileId()).isEqualTo(5L);
        assertThat(review.getStudentProfileId()).isEqualTo(7L);
        assertThat(review.getPositivePoints()).containsExactly(ReviewPositivePoint.ON_TIME_DELIVERY);
        assertThat(review.getContent()).isEqualTo("꼼꼼하게 작업해 주셨어요.");
        assertThat(review.getRating()).isEqualTo(4);
    }

    @Test
    @DisplayName("좋은 점이 없으면 빈 목록으로 저장한다")
    void savesEmptyPositivePoints() {
        when(reviewRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Review review = reviewService.createReview(command(null), 5L, 7L);

        assertThat(review.getPositivePoints()).isEmpty();
    }

    @Test
    @DisplayName("이미 리뷰가 있는 의뢰면 REVIEW_409_DUPLICATE로 거부하고 저장하지 않는다")
    void rejectsExistingReview() {
        when(reviewRepository.existsByJobId(42L)).thenReturn(true);

        assertError(() -> reviewService.createReview(command(List.of()), 5L, 7L), ErrorCode.REVIEW_ALREADY_EXISTS);
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동시 작성으로 유니크 제약이 충돌하면 REVIEW_409_DUPLICATE로 바꿔 거부한다")
    void convertsUniqueViolation() {
        when(reviewRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("reviews_job_id_key"));

        assertError(() -> reviewService.createReview(command(List.of()), 5L, 7L), ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("요청한 학생이 받은 의뢰 리뷰를 반환한다")
    void getsStudentReview() {
        Review review = Review.builder().id(301L).jobId(42L).studentProfileId(7L).build();
        when(reviewRepository.findByJobIdAndStudentProfileId(42L, 7L)).thenReturn(Optional.of(review));

        assertThat(reviewService.getStudentReview(42L, 7L)).isSameAs(review);
    }

    @Test
    @DisplayName("리뷰가 없거나 다른 학생의 리뷰면 REVIEW_404로 거부한다")
    void rejectsMissingStudentReview() {
        when(reviewRepository.findByJobIdAndStudentProfileId(42L, 8L)).thenReturn(Optional.empty());

        assertError(() -> reviewService.getStudentReview(42L, 8L), ErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("요청한 사장님이 작성한 의뢰 리뷰를 반환한다")
    void getsOwnerReview() {
        Review review = Review.builder().id(301L).jobId(42L).ownerProfileId(5L).build();
        when(reviewRepository.findByJobIdAndOwnerProfileId(42L, 5L)).thenReturn(Optional.of(review));

        assertThat(reviewService.getOwnerReview(42L, 5L)).isSameAs(review);
    }

    @Test
    @DisplayName("리뷰가 없거나 다른 사장님이 작성한 리뷰면 REVIEW_404로 거부한다")
    void rejectsMissingOwnerReview() {
        when(reviewRepository.findByJobIdAndOwnerProfileId(42L, 6L)).thenReturn(Optional.empty());
        when(reviewRepository.findByJobIdAndOwnerProfileId(43L, 5L)).thenReturn(Optional.empty());

        assertError(() -> reviewService.getOwnerReview(42L, 6L), ErrorCode.REVIEW_NOT_FOUND);
        assertError(() -> reviewService.getOwnerReview(43L, 5L), ErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("학생이 받은 리뷰를 작성 시각 내림차순, 같은 시각은 ID 내림차순으로 정렬하고 작성 시각이 없으면 마지막에 둔다")
    void sortsStudentReviewsByLatest() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 30, 12, 0);
        when(reviewRepository.findByStudentProfileId(7L)).thenReturn(List.of(
                review(1L, null), review(2L, createdAt), review(3L, createdAt.plusSeconds(1)), review(4L, createdAt)));

        assertThat(reviewService.getStudentReviews(7L))
                .extracting(Review::getId)
                .containsExactly(3L, 4L, 2L, 1L);
    }

    @Test
    @DisplayName("학생이 받은 리뷰가 없으면 빈 목록을 반환한다")
    void returnsEmptyStudentReviews() {
        when(reviewRepository.findByStudentProfileId(7L)).thenReturn(List.of());

        assertThat(reviewService.getStudentReviews(7L)).isEmpty();
    }

    private static Review review(Long id, LocalDateTime createdAt) {
        return Review.builder().id(id).studentProfileId(7L).createdAt(createdAt).build();
    }

    private static CreateReviewCommand command(List<ReviewPositivePoint> positivePoints) {
        return CreateReviewCommand.of("KAKAO_12345", 42L, positivePoints, "꼼꼼하게 작업해 주셨어요.", 4);
    }

    private static void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    @Test
    @DisplayName("평균 별점은 소수 첫째 자리까지 HALF_UP으로 반올림한다")
    void roundsAverageRatingHalfUp() {
        when(reviewRepository.findAverageRatingByStudentProfileId(7L)).thenReturn(4.25);
        when(reviewRepository.findAverageRatingByStudentProfileId(8L)).thenReturn(4.35);
        when(reviewRepository.findAverageRatingByStudentProfileId(9L)).thenReturn(5.0);

        assertThat(reviewService.getAverageRating(7L)).isEqualByComparingTo("4.3");
        assertThat(reviewService.getAverageRating(8L)).isEqualByComparingTo("4.4");
        assertThat(reviewService.getAverageRating(9L)).hasToString("5.0");
    }

    @Test
    @DisplayName("받은 리뷰가 없으면 평균 별점은 0.0이다")
    void returnsZeroAverageWithoutReviews() {
        when(reviewRepository.findAverageRatingByStudentProfileId(7L)).thenReturn(null);

        assertThat(reviewService.getAverageRating(7L)).hasToString("0.0");
    }

    @Test
    @DisplayName("학생별 평균 별점을 한 번에 조회해 HALF_UP으로 반올림하고 리뷰가 없는 학생은 0.0으로 채운다")
    void returnsAverageRatingsForAllRequestedStudents() {
        List<StudentAverageRating> rows = List.of(averageRating(7L, 4.25), averageRating(8L, 4.35));
        when(reviewRepository.findAverageRatingsByStudentProfileIds(List.of(7L, 8L, 9L))).thenReturn(rows);

        Map<Long, BigDecimal> ratings = reviewService.getAverageRatings(List.of(7L, 8L, 9L));

        assertThat(ratings).containsOnlyKeys(7L, 8L, 9L);
        assertThat(ratings.get(7L)).hasToString("4.3");
        assertThat(ratings.get(8L)).hasToString("4.4");
        assertThat(ratings.get(9L)).hasToString("0.0");
    }

    @Test
    @DisplayName("대상 학생이 없으면 평균 별점을 조회하지 않는다")
    void skipsAverageRatingQueryWithoutStudents() {
        assertThat(reviewService.getAverageRatings(List.of())).isEmpty();
        verifyNoInteractions(reviewRepository);
    }

    private static StudentAverageRating averageRating(Long studentProfileId, Double average) {
        StudentAverageRating row = mock(StudentAverageRating.class);
        when(row.getStudentProfileId()).thenReturn(studentProfileId);
        when(row.getAverageRating()).thenReturn(average);
        return row;
    }
}
