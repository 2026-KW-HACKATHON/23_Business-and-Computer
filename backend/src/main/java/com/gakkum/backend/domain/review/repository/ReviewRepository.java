package com.gakkum.backend.domain.review.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByJobId(Long jobId);

    Optional<Review> findByJobIdAndStudentProfileId(Long jobId, Long studentProfileId);
}
