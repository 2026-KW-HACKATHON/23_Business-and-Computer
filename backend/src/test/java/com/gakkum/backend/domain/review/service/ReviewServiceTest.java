package com.gakkum.backend.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.gakkum.backend.domain.review.dto.ReviewCommandDto.CreateReviewCommand;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;
import com.gakkum.backend.domain.review.repository.ReviewRepository;
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

    private static CreateReviewCommand command(List<ReviewPositivePoint> positivePoints) {
        return CreateReviewCommand.of("KAKAO_12345", 42L, positivePoints, "꼼꼼하게 작업해 주셨어요.", 4);
    }

    private static void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
