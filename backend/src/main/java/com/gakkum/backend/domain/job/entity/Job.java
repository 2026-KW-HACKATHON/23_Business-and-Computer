package com.gakkum.backend.domain.job.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_profile_id", nullable = false)
    private Long ownerProfileId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Long budget;

    @Column(name = "draft_deadline", nullable = false)
    private LocalDate draftDeadline;

    @Column(name = "final_deadline", nullable = false)
    private LocalDate finalDeadline;

    @Column(name = "revision_count", nullable = false)
    private Integer revisionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobStatus status;

    @Column(name = "selected_student_profile_id")
    private Long selectedStudentProfileId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Job create(
            Long ownerProfileId,
            String title,
            String description,
            Long budget,
            LocalDate draftDeadline,
            LocalDate finalDeadline,
            Integer revisionCount) {
        return Job.builder()
                .ownerProfileId(ownerProfileId)
                .title(title)
                .description(description)
                .budget(budget)
                .draftDeadline(draftDeadline)
                .finalDeadline(finalDeadline)
                .revisionCount(revisionCount)
                .status(JobStatus.OPEN)
                .build();
    }
}
