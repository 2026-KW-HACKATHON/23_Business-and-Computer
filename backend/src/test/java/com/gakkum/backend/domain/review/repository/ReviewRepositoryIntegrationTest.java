package com.gakkum.backend.domain.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobRepository.StudentJobCount;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.review.dto.ReviewCommandDto.CreateReviewCommand;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;
import com.gakkum.backend.domain.review.repository.ReviewRepository.StudentAverageRating;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

/** 동시 작성은 트랜잭션을 나눠 커밋해야 하므로 클래스 트랜잭션 대신 직접 데이터를 정리한다. */
@SpringBootTest
@ActiveProfiles("local")
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
    @DisplayName("PostgreSQL에 글 없는 리뷰를 NULL로 저장하고 조회하며 리뷰 수와 평균 별점에 포함한다")
    void storesReviewWithoutContentAndCountsIt() {
        long student = 987_040L;
        Job job = saveClosedJob();
        Review withoutContent = reviewRepository.saveAndFlush(Review.create(job.getId(), 5L, student,
                List.of(ReviewPositivePoint.KINDNESS), null, 2));
        reviewRepository.saveAndFlush(Review.create(saveClosedJob().getId(), 5L, student, List.of(), "좋았어요.", 5));

        Review found = reviewRepository.findByJobIdAndStudentProfileId(job.getId(), student).orElseThrow();
        assertThat(found.getId()).isEqualTo(withoutContent.getId());
        assertThat(found.getContent()).isNull();
        assertThat(found.getPositivePoints()).containsExactly(ReviewPositivePoint.KINDNESS);
        assertThat(reviewService.getStudentReviews(student)).extracting(Review::getContent)
                .containsExactlyInAnyOrder(null, "좋았어요.");
        assertThat(reviewService.countStudentReviews(student)).isEqualTo(2L);
        assertThat(reviewRepository.findAverageRatingByStudentProfileId(student)).isEqualTo(3.5);
        assertThat(reviewService.getAverageRatings(List.of(student)).get(student)).hasToString("3.5");
        assertThat(reviewService.getReviewedJobIds(List.of(job.getId()))).containsExactly(job.getId());
    }

    @Test
    @DisplayName("PostgreSQL에서 의뢰 ID와 리뷰 대상 학생 프로필 ID가 모두 맞을 때만 리뷰를 찾는다")
    void findsReviewByJobAndStudent() {
        Job job = saveClosedJob();
        Review saved = reviewRepository.saveAndFlush(Review.create(job.getId(), 5L, 7L,
                List.of(ReviewPositivePoint.KINDNESS), "친절했어요.", 5));

        assertThat(reviewRepository.findByJobIdAndStudentProfileId(job.getId(), 7L))
                .get().extracting(Review::getId).isEqualTo(saved.getId());
        assertThat(reviewRepository.findByJobIdAndStudentProfileId(job.getId(), 8L)).isEmpty();
        assertThat(reviewRepository.findByJobIdAndStudentProfileId(saveClosedJob().getId(), 7L)).isEmpty();
    }

    @Test
    @DisplayName("PostgreSQL에서 학생별 평균 별점을 집계하고 리뷰가 없으면 null을 반환한다")
    void averagesRatingPerStudent() {
        long student = 987_001L;
        long other = 987_002L;
        long none = 987_003L;
        reviewRepository.saveAndFlush(Review.create(saveClosedJob().getId(), 5L, student, List.of(), "a", 4));
        reviewRepository.saveAndFlush(Review.create(saveClosedJob().getId(), 5L, student, List.of(), "b", 5));
        reviewRepository.saveAndFlush(Review.create(saveClosedJob().getId(), 5L, other, List.of(), "c", 1));

        assertThat(reviewRepository.findAverageRatingByStudentProfileId(student)).isEqualTo(4.5);
        assertThat(reviewRepository.findAverageRatingByStudentProfileId(other)).isEqualTo(1.0);
        assertThat(reviewRepository.findAverageRatingByStudentProfileId(none)).isNull();
    }

    @Test
    @DisplayName("PostgreSQL에서 학생의 CLOSED 의뢰만 리뷰 유무와 무관하게 센다")
    void countsClosedJobsPerStudent() {
        long student = 987_010L;
        long other = 987_011L;
        Job reviewed = saveJob(student, true, false);
        saveJob(student, true, false);
        saveJob(student, false, false);
        saveJob(student, false, true);
        saveJob(other, true, false);
        reviewRepository.saveAndFlush(Review.create(reviewed.getId(), 5L, student, List.of(), "리뷰", 5));

        assertThat(jobRepository.countBySelectedStudentProfileIdAndStatus(student, JobStatus.CLOSED)).isEqualTo(2L);
        assertThat(jobRepository.countBySelectedStudentProfileIdAndStatus(987_012L, JobStatus.CLOSED)).isZero();
    }

    @Test
    @DisplayName("PostgreSQL에서 요청한 학생만 학생별 평균 별점을 집계하고 리뷰가 없는 학생은 행이 없다")
    void averagesRatingsPerRequestedStudents() {
        long student = 987_020L;
        long other = 987_021L;
        long none = 987_022L;
        long excluded = 987_023L;
        reviewRepository.saveAndFlush(Review.create(saveClosedJob().getId(), 5L, student, List.of(), "a", 4));
        reviewRepository.saveAndFlush(Review.create(saveClosedJob().getId(), 5L, student, List.of(), "b", 4));
        reviewRepository.saveAndFlush(Review.create(saveClosedJob().getId(), 5L, student, List.of(), "c", 5));
        reviewRepository.saveAndFlush(Review.create(saveClosedJob().getId(), 5L, other, List.of(), "d", 1));
        reviewRepository.saveAndFlush(Review.create(saveClosedJob().getId(), 5L, excluded, List.of(), "e", 5));

        assertThat(reviewRepository.findAverageRatingsByStudentProfileIds(List.of(student, other, none)))
                .extracting(StudentAverageRating::getStudentProfileId)
                .containsExactlyInAnyOrder(student, other);
        assertThat(reviewService.getAverageRatings(List.of(student, other, none)))
                .containsOnlyKeys(student, other, none)
                .satisfies(ratings -> {
                    assertThat(ratings.get(student)).hasToString("4.3");
                    assertThat(ratings.get(other)).hasToString("1.0");
                    assertThat(ratings.get(none)).hasToString("0.0");
                });
    }

    @Test
    @DisplayName("PostgreSQL에서 요청한 학생만 학생별 CLOSED 의뢰 수를 리뷰 유무와 무관하게 세고 취소 건은 뺀다")
    void countsClosedJobsPerRequestedStudents() {
        long student = 987_030L;
        long other = 987_031L;
        long none = 987_032L;
        long excluded = 987_033L;
        Job reviewed = saveJob(student, true, false);
        saveJob(student, true, false);
        saveJob(student, false, false);
        saveJob(student, false, true);
        saveJob(other, true, false);
        saveJob(none, false, true);
        saveJob(excluded, true, false);
        reviewRepository.saveAndFlush(Review.create(reviewed.getId(), 5L, student, List.of(), "리뷰", 5));

        assertThat(jobRepository.countByStudentProfileIdsAndStatus(List.of(student, other, none), JobStatus.CLOSED))
                .extracting(StudentJobCount::getStudentProfileId, StudentJobCount::getJobCount)
                .containsExactlyInAnyOrder(tuple(student, 2L), tuple(other, 1L));
        assertThat(jobService.countClosedJobsByStudentProfileIds(List.of(student, other, none)))
                .containsOnly(Map.entry(student, 2L), Map.entry(other, 1L), Map.entry(none, 0L));
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

    private Job saveJob(long studentProfileId, boolean closed, boolean cancelled) {
        Job job = Job.create(5L, "집계 테스트 의뢰", "설명", 50000L,
                LocalDateTime.now().toLocalDate(), LocalDateTime.now().toLocalDate().plusDays(3), 2, null);
        job.match(studentProfileId);
        if (closed) {
            job.complete(LocalDateTime.now());
        }
        if (cancelled) {
            job.cancel(LocalDateTime.now(), "취소 이유", "남길 말");
        }
        Job saved = jobRepository.saveAndFlush(job);
        jobIds.add(saved.getId());
        return saved;
    }

    private Job saveClosedJob() {
        Job job = Job.create(5L, "리뷰 테스트 의뢰", "설명", 50000L,
                LocalDateTime.now().toLocalDate(), LocalDateTime.now().toLocalDate().plusDays(3), 2, null);
        job.match(7L);
        job.complete(LocalDateTime.now());
        Job saved = jobRepository.saveAndFlush(job);
        jobIds.add(saved.getId());
        return saved;
    }
}
