package com.gakkum.backend.domain.job.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

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
@Table(name = "job_applications", uniqueConstraints = @UniqueConstraint(
        name = JobApplication.JOB_STUDENT_UNIQUE_CONSTRAINT,
        columnNames = { "job_id", "student_profile_id" }))
public class JobApplication {

    public static final String JOB_STUDENT_UNIQUE_CONSTRAINT = "job_applications_job_id_student_profile_id_key";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_profile_id", nullable = false)
    private Long studentProfileId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(nullable = false)
    private String summary;

    @Column(name = "work_plan", nullable = false, length = 500)
    private String workPlan;

    @Column(name = "delivery_method", nullable = false, length = 500)
    private String deliveryMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobApplicationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 새 지원서는 항상 대기 중(PENDING)으로 시작한다. */
    public static JobApplication create(
            Long studentProfileId, Long jobId, String summary, String workPlan, String deliveryMethod) {
        return JobApplication.builder()
                .studentProfileId(studentProfileId)
                .jobId(jobId)
                .summary(summary)
                .workPlan(workPlan)
                .deliveryMethod(deliveryMethod)
                .status(JobApplicationStatus.PENDING)
                .build();
    }

    public void accept() {
        if (status == JobApplicationStatus.ACCEPTED) {
            return;
        }
        if (status != JobApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        status = JobApplicationStatus.ACCEPTED;
    }
}
