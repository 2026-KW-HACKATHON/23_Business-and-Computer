package com.gakkum.backend.domain.job.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "job_submissions", uniqueConstraints = @UniqueConstraint(
        name = "job_submissions_job_id_revision_number_key",
        columnNames = { "job_id", "revision_number" }))
public class JobSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_type", nullable = false, length = 30)
    private JobSubmissionType submissionType;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_urls", nullable = false, columnDefinition = "jsonb")
    private List<String> fileUrls;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private JobSubmissionReviewStatus reviewStatus;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static JobSubmission create(
            Long jobId,
            JobSubmissionType submissionType,
            Integer revisionNumber,
            List<String> fileUrls,
            String message) {
        return JobSubmission.builder()
                .jobId(jobId)
                .submissionType(submissionType)
                .revisionNumber(revisionNumber)
                .fileUrls(List.copyOf(fileUrls))
                .message(message)
                .reviewStatus(JobSubmissionReviewStatus.PENDING)
                .build();
    }
}
