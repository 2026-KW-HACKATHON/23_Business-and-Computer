package com.gakkum.backend.domain.review.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.review.dto.ReviewCommandDto.CreateReviewCommand;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.repository.ReviewRepository;
import com.gakkum.backend.domain.review.repository.ReviewRepository.StudentAverageRating;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;

    /**
     * 의뢰의 리뷰를 저장한다. 호출하는 쪽은 의뢰 행을 잠근 트랜잭션 안에서 호출해야 한다.
     * 잠금을 거치지 않은 동시 작성은 job_id 유니크 제약 충돌로 막고 중복 작성으로 본다.
     * @param command
     * @param ownerProfileId 리뷰를 쓰는 사장님 프로필 ID
     * @param studentProfileId 의뢰를 수행한 학생 프로필 ID
     * @return 저장된 리뷰
     */
    @Transactional
    public Review createReview(CreateReviewCommand command, Long ownerProfileId, Long studentProfileId) {
        if (reviewRepository.existsByJobId(command.getJobId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        try {
            return reviewRepository.saveAndFlush(Review.create(
                    command.getJobId(), ownerProfileId, studentProfileId, command.getPositivePoints(),
                    command.getContent(), command.getRating()));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
    }

    /**
     * 학생이 받은 의뢰 리뷰 단건 조회.
     * 의뢰가 없거나, 요청한 학생이 리뷰 대상이 아니거나, 리뷰가 없으면 모두 같은 404로 거부한다.
     * @param jobId
     * @param studentProfileId 요청한 학생 프로필 ID
     * @return 요청한 학생이 받은 리뷰
     */
    @Transactional(readOnly = true)
    public Review getStudentReview(Long jobId, Long studentProfileId) {
        return reviewRepository.findByJobIdAndStudentProfileId(jobId, studentProfileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }

    /**
     * 학생이 모든 사장님에게 받은 리뷰 전체를 작성 시각 내림차순, 같은 시각은 리뷰 ID 내림차순으로 조회한다.
     * 작성 시각이 없는 기존 데이터는 마지막에 둔다.
     * @param studentProfileId 학생 프로필 ID
     */
    @Transactional(readOnly = true)
    public List<Review> getStudentReviews(Long studentProfileId) {
        return reviewRepository.findByStudentProfileId(studentProfileId).stream()
                .sorted(Comparator
                        .comparing(Review::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Review::getId, Comparator.reverseOrder()))
                .toList();
    }

    /**
     * 학생이 모든 사장님에게 받은 리뷰 중 최신 limit개를 전체 조회와 같은 순서로 조회한다.
     * @param studentProfileId 학생 프로필 ID
     * @param limit 최대 개수
     */
    @Transactional(readOnly = true)
    public List<Review> getLatestStudentReviews(Long studentProfileId, int limit) {
        return reviewRepository.findLatestByStudentProfileId(studentProfileId, Limit.of(limit));
    }

    /** 리뷰가 있는 의뢰 ID. 사장님 끝난 의뢰 목록에서 후기를 남겼는지 가른다. */
    @Transactional(readOnly = true)
    public Set<Long> getReviewedJobIds(Collection<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return Set.of();
        }
        return reviewRepository.findByJobIdIn(jobIds).stream()
                .map(Review::getJobId)
                .collect(Collectors.toSet());
    }

    /** 학생이 모든 사장님에게 받은 전체 리뷰 수. */
    @Transactional(readOnly = true)
    public long countStudentReviews(Long studentProfileId) {
        return reviewRepository.countByStudentProfileId(studentProfileId);
    }

    /**
     * 학생이 받은 전체 리뷰의 평균 별점을 소수 첫째 자리까지 HALF_UP으로 반올림해 반환한다.
     * 리뷰가 없으면 0.0이다.
     * @param studentProfileId 학생 프로필 ID
     */
    @Transactional(readOnly = true)
    public BigDecimal getAverageRating(Long studentProfileId) {
        return roundAverageRating(reviewRepository.findAverageRatingByStudentProfileId(studentProfileId));
    }

    /**
     * 학생별 평균 별점을 한 번에 조회한다. 반올림 기준은 단건 조회와 같다.
     * @param studentProfileIds 학생 프로필 ID 목록
     * @return 요청한 모든 학생 프로필 ID별 평균 별점, 리뷰가 없는 학생은 0.0
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getAverageRatings(Collection<Long> studentProfileIds) {
        if (studentProfileIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> averages = reviewRepository.findAverageRatingsByStudentProfileIds(studentProfileIds).stream()
                .collect(Collectors.toMap(StudentAverageRating::getStudentProfileId, StudentAverageRating::getAverageRating));
        return studentProfileIds.stream()
                .distinct()
                .collect(Collectors.toMap(Function.identity(), id -> roundAverageRating(averages.get(id))));
    }

    private BigDecimal roundAverageRating(Double average) {
        if (average == null) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
    }
}
