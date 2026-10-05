package com.gakkum.backend.domain.review.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gakkum.backend.domain.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByJobId(Long jobId);

    Optional<Review> findByJobIdAndStudentProfileId(Long jobId, Long studentProfileId);

    List<Review> findByStudentProfileId(Long studentProfileId);

    /** 학생이 받은 전체 리뷰의 평균 별점. 리뷰가 없으면 null이다. */
    @Query("select avg(r.rating) from Review r where r.studentProfileId = :studentProfileId")
    Double findAverageRatingByStudentProfileId(@Param("studentProfileId") Long studentProfileId);

    /*
     * 학생별 평균 별점은 GROUP BY 집계가 필요해 메서드 이름으로 표현할 수 없다.
     * 리뷰가 없는 학생은 행이 없다.
     */
    @Query("""
            select r.studentProfileId as studentProfileId, avg(r.rating) as averageRating
            from Review r
            where r.studentProfileId in :studentProfileIds
            group by r.studentProfileId
            """)
    List<StudentAverageRating> findAverageRatingsByStudentProfileIds(
            @Param("studentProfileIds") Collection<Long> studentProfileIds);

    interface StudentAverageRating {
        Long getStudentProfileId();

        Double getAverageRating();
    }
}
