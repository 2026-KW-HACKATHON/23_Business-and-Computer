package com.gakkum.backend.domain.proposal.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

/** 학생이 기존 의뢰와 무관하게 특정 사장님에게 보낸 작업 제안. 기간은 사장님 수락일 기준 일수다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "proposals")
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_profile_id", nullable = false)
    private Long studentProfileId;

    @Column(name = "owner_profile_id", nullable = false)
    private Long ownerProfileId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "customer_problem", nullable = false, length = 500)
    private String customerProblem;

    @Column(name = "proposed_solution", nullable = false, length = 500)
    private String proposedSolution;

    @Column(name = "work_plan", nullable = false, length = 500)
    private String workPlan;

    @Column(name = "proposed_fee", nullable = false)
    private Long proposedFee;

    // 0이면 수락 당일
    @Column(name = "draft_days", nullable = false)
    private Integer draftDays;

    @Column(name = "final_days", nullable = false)
    private Integer finalDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reference_image_urls", nullable = false, columnDefinition = "jsonb")
    private List<String> referenceImageUrls;

    // 좋아요 수. 누른 학생은 proposal_likes에 기록한다
    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposalStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static Proposal create(
            Long studentProfileId,
            Long ownerProfileId,
            String title,
            String customerProblem,
            String proposedSolution,
            String workPlan,
            Long proposedFee,
            Integer draftDays,
            Integer finalDays,
            List<String> referenceImageUrls) {
        return Proposal.builder()
                .studentProfileId(studentProfileId)
                .ownerProfileId(ownerProfileId)
                .title(title)
                .customerProblem(customerProblem)
                .proposedSolution(proposedSolution)
                .workPlan(workPlan)
                .proposedFee(proposedFee)
                .draftDays(draftDays)
                .finalDays(finalDays)
                .referenceImageUrls(List.copyOf(referenceImageUrls))
                .likeCount(0)
                .status(ProposalStatus.PENDING)
                .build();
    }

    /** 기준일에 초안 기간을 더한 초안 마감일. 기준일은 결제 승인 시각의 한국 날짜다. */
    public LocalDate draftDeadlineFrom(LocalDate baseDate) {
        return baseDate.plusDays(draftDays);
    }

    /** 기준일에 최종 기간을 더한 최종 마감일. */
    public LocalDate finalDeadlineFrom(LocalDate baseDate) {
        return baseDate.plusDays(finalDays);
    }

    /** 제안으로 만드는 의뢰의 설명. 고객 문제·해결 방안·작업 계획을 구분해 합친다. */
    public String toJobDescription() {
        return "[고객 문제]\n" + customerProblem
                + "\n\n[해결 방안]\n" + proposedSolution
                + "\n\n[작업 계획]\n" + workPlan;
    }

    /** 사장님 결제가 승인되어 학생의 작업 시작을 기다린다. 결제 전(PENDING) 제안만 넘어갈 수 있다. */
    public void awaitStart() {
        if (status != ProposalStatus.PENDING) {
            throw new BusinessException(ErrorCode.PROPOSAL_PAYMENT_NOT_AVAILABLE);
        }
        status = ProposalStatus.AWAITING_START;
    }

    /** 학생이 작업을 시작해 제안이 수락된다. 이미 수락된 제안의 재요청은 그대로 둔다. */
    public void accept() {
        if (status == ProposalStatus.ACCEPTED) {
            return;
        }
        if (status != ProposalStatus.AWAITING_START) {
            throw new BusinessException(ErrorCode.JOB_START_NOT_AVAILABLE);
        }
        status = ProposalStatus.ACCEPTED;
    }
}
