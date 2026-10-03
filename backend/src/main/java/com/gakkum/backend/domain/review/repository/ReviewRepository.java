package com.gakkum.backend.domain.review.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gakkum.backend.domain.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByJobId(Long jobId);

    Optional<Review> findByJobIdAndStudentProfileId(Long jobId, Long studentProfileId);

    /** 학생이 받은 전체 리뷰의 평균 별점. 리뷰가 없으면 null이다. */
    @Query("select avg(r.rating) from Review r where r.studentProfileId = :studentProfileId")
    Double findAverageRatingByStudentProfileId(@Param("studentProfileId") Long studentProfileId);
}
