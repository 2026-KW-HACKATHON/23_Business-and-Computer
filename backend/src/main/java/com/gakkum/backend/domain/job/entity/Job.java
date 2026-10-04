package com.gakkum.backend.domain.job.entity;

import java.time.LocalDate;
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

    // 완료(CLOSED) 또는 취소(CANCELLED)로 종료된 시각
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 사장님이 취소 시 입력한 취소 이유. 취소되지 않았거나 입력 도입 전 취소 건은 null
    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    // 사장님이 취소 시 학생에게 남긴 말. 취소되지 않았거나 입력 도입 전 취소 건은 null
    @Column(name = "message_to_student", columnDefinition = "TEXT")
    private String messageToStudent;

    // 이 의뢰를 만든 제안. 사장님이 직접 올린 일반 의뢰는 null
    @Column(name = "proposal_id")
    private Long proposalId;

    // 학생이 제안 의뢰의 작업을 시작한 시각. 일반 의뢰와 시작 전 제안 의뢰는 null
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    // 사장님이 제안 결제 시 학생에게 남긴 한마디. 취소 시 남기는 messageToStudent와 별개다
    @Column(name = "acceptance_message", columnDefinition = "TEXT")
    private String acceptanceMessage;

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

    /** 결제가 승인된 제안으로 만드는 수락 대기(AWAITING_START) 의뢰. 담당 학생과 마감일은 이때 확정한다. */
    public static Job createAwaitingStart(
            Long ownerProfileId,
            Long studentProfileId,
            Long proposalId,
            String title,
            String description,
            Long budget,
            LocalDate draftDeadline,
            LocalDate finalDeadline,
            Integer revisionCount,
            String acceptanceMessage) {
        return Job.builder()
                .ownerProfileId(ownerProfileId)
                .selectedStudentProfileId(studentProfileId)
                .proposalId(proposalId)
                .title(title)
                .description(description)
                .budget(budget)
                .draftDeadline(draftDeadline)
                .finalDeadline(finalDeadline)
                .revisionCount(revisionCount)
                .acceptanceMessage(acceptanceMessage)
                .status(JobStatus.AWAITING_START)
                .build();
    }

    /**
     * 수락 대기(AWAITING_START) 제안 의뢰를 진행 중(MATCHED)으로 넘기고 시작 시각을 기록한다. 마감일은 바꾸지 않는다.
     * 이미 시작한 제안 의뢰의 재요청은 기존 시작 시각을 그대로 둔다.
     */
    public void start(LocalDateTime startedAt) {
        if (status == JobStatus.MATCHED && proposalId != null && this.startedAt != null) {
            return;
        }
        if (status != JobStatus.AWAITING_START) {
            throw new BusinessException(ErrorCode.JOB_START_NOT_AVAILABLE);
        }
        status = JobStatus.MATCHED;
        this.startedAt = startedAt;
    }

    public void match(Long studentProfileId) {
        if (status == JobStatus.MATCHED && studentProfileId != null
                && studentProfileId.equals(selectedStudentProfileId)) {
            return;
        }
        if (status != JobStatus.OPEN || selectedStudentProfileId != null || studentProfileId == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        status = JobStatus.MATCHED;
        selectedStudentProfileId = studentProfileId;
    }

    /** 진행 중(MATCHED) 의뢰만 종료(CLOSED)하고 완료 시각을 기록한다. */
    public void complete(LocalDateTime completedAt) {
        if (status != JobStatus.MATCHED) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_REVIEW_NOT_AVAILABLE);
        }
        status = JobStatus.CLOSED;
        this.completedAt = completedAt;
    }

    /** 모집 중(OPEN) 또는 진행 중(MATCHED) 의뢰만 취소(CANCELLED)하고 종료 시각과 취소 이유·남길 말을 기록한다. */
    public void cancel(LocalDateTime cancelledAt, String cancelReason, String messageToStudent) {
        if (status != JobStatus.OPEN && status != JobStatus.MATCHED) {
            throw new BusinessException(ErrorCode.JOB_CANCEL_NOT_AVAILABLE);
        }
        status = JobStatus.CANCELLED;
        this.completedAt = cancelledAt;
        this.cancelReason = cancelReason;
        this.messageToStudent = messageToStudent;
    }
}
