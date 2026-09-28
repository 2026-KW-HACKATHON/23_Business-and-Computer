package com.gakkum.backend.domain.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.review.dto.ReviewCommandDto.CreateReviewCommand;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

/** 동시 작성은 트랜잭션을 나눠 커밋해야 하므로 클래스 트랜잭션 대신 직접 데이터를 정리한다. */
@SpringBootTest
class ReviewRepositoryIntegrationTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobService jobService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> jobIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            reviewRepository.findAll().stream()
                    .filter(review -> jobIds.contains(review.getJobId()))
                    .forEach(reviewRepository::delete);
            jobRepository.deleteAllById(jobIds);
        });
    }

    @Test
    @DisplayName("PostgreSQL에 좋은 점 목록을 JSONB 배열로 저장하고 같은 순서로 읽는다")
    void storesPositivePointsAsJsonb() {
        Job job = saveClosedJob();
        Review saved = reviewRepository.saveAndFlush(Review.create(job.getId(), 5L, 7L,
                List.of(ReviewPositivePoint.REVISION_FEEDBACK, ReviewPositivePoint.QUALITY_OUTPUT),
                "수정을 잘 반영해 주셨어요.", 5));
        Review empty = reviewRepository.saveAndFlush(Review.create(saveClosedJob().getId(), 5L, 7L,
                List.of(), "좋았어요.", 3));

        Review found = reviewRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getPositivePoints())
                .containsExactly(ReviewPositivePoint.REVISION_FEEDBACK, ReviewPositivePoint.QUALITY_OUTPUT);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(reviewRepository.findById(empty.getId()).orElseThrow().getPositivePoints()).isEmpty();
        assertThat(reviewRepository.existsByJobId(job.getId())).isTrue();
    }

    @Test
    @DisplayName("PostgreSQL은 같은 의뢰의 두 번째 리뷰를 유니크 제약으로 거부한다")
    void rejectsSecondReviewForSameJob() {
        Job job = saveClosedJob();
        reviewRepository.saveAndFlush(Review.create(job.getId(), 5L, 7L, List.of(), "첫 리뷰", 5));

        assertThatThrownBy(() -> reviewRepository.saveAndFlush(
                Review.create(job.getId(), 5L, 7L, List.of(), "두 번째 리뷰", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL은 1~5 밖의 별점을 체크 제약으로 거부한다")
    void rejectsOutOfRangeRating() {
        Job job = saveClosedJob();

        assertThatThrownBy(() -> reviewRepository.saveAndFlush(
                Review.create(job.getId(), 5L, 7L, List.of(), "별점 오류", 6)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 의뢰에 리뷰를 동시에 쓰면 의뢰 행 잠금으로 하나만 저장되고 나머지는 REVIEW_409_DUPLICATE로 거부된다")
    void allowsOnlyOneConcurrentReview() throws Exception {
        Job job = saveClosedJob();
        int writers = 4;
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        List<Future<Object>> results = new ArrayList<>();

        try {
            for (int i = 0; i < writers; i++) {
                int rating = i + 1;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return transactionTemplate.execute(status -> {
                            Job locked = jobService.getReviewableJobForUpdate(job.getId(), 5L);
                            return reviewService.createReview(
                                    CreateReviewCommand.of("KAKAO_12345", job.getId(), List.of(), "동시 리뷰", rating),
                                    5L, locked.getSelectedStudentProfileId());
                        });
                    } catch (BusinessException exception) {
                        return exception.getErrorCode();
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(10, TimeUnit.SECONDS));
            }
            assertThat(outcomes).filteredOn(Review.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(ErrorCode.class::isInstance)
                    .hasSize(writers - 1)
                    .containsOnly(ErrorCode.REVIEW_ALREADY_EXISTS);
            assertThat(reviewRepository.findAll()).filteredOn(review -> review.getJobId().equals(job.getId()))
                    .singleElement()
                    .extracting(Review::getStudentProfileId)
                    .isEqualTo(7L);
        } finally {
            executor.shutdownNow();
        }
    }

    private Job saveClosedJob() {
        Job job = Job.create(5L, "리뷰 테스트 의뢰", "설명", 50000L,
                LocalDateTime.now().toLocalDate(), LocalDateTime.now().toLocalDate().plusDays(3), 2);
        job.match(7L);
        job.complete(LocalDateTime.now());
        Job saved = jobRepository.saveAndFlush(job);
        jobIds.add(saved.getId());
        return saved;
    }
}
