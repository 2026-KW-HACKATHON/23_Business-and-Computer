package com.gakkum.backend.domain.review.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 완료된 의뢰의 사장님이 담당 학생에게 남긴 리뷰. 의뢰당 하나만 존재한다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(
        name = "reviews_job_id_key", columnNames = { "job_id" }))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "owner_profile_id", nullable = false)
    private Long ownerProfileId;

    @Column(name = "student_profile_id", nullable = false)
    private Long studentProfileId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "positive_points", nullable = false, columnDefinition = "jsonb")
    private List<ReviewPositivePoint> positivePoints;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Integer rating;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static Review create(
            Long jobId,
            Long ownerProfileId,
            Long studentProfileId,
            List<ReviewPositivePoint> positivePoints,
            String content,
            Integer rating) {
        return Review.builder()
                .jobId(jobId)
                .ownerProfileId(ownerProfileId)
                .studentProfileId(studentProfileId)
                .positivePoints(List.copyOf(positivePoints))
                .content(content)
                .rating(rating)
                .build();
    }
}
