package com.gakkum.backend.domain.review.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.review.dto.ReviewCommandDto.CreateReviewCommand;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.repository.ReviewRepository;
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
}
