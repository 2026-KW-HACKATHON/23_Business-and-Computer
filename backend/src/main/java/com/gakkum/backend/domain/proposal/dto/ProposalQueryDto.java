package com.gakkum.backend.domain.proposal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.RefundedPaymentData;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalRejectedBy;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.user.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class ProposalQueryDto {

    private ProposalQueryDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalCreateResult {

        private final Long proposalId;

        public static ProposalCreateResult from(Proposal proposal) {
            return ProposalCreateResult.builder()
                    .proposalId(proposal.getId())
                    .build();
        }
    }

    /** 공감 켜기·끄기 결과. 공감 수는 이번 변경이 반영된 값이다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalLikeResult {

        private final Long proposalId;
        private final Integer likeCount;
        private final boolean likedByMe;

        public static ProposalLikeResult of(Proposal proposal, boolean likedByMe) {
            return ProposalLikeResult.builder()
                    .proposalId(proposal.getId())
                    .likeCount(proposal.getLikeCount())
                    .likedByMe(likedByMe)
                    .build();
        }
    }

    /** 공감 요청을 처리한 제안과, 이 요청으로 공감 기록이 새로 저장됐는지. 이미 공감한 제안의 재요청은 false다. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalLikeData {

        private final Proposal proposal;
        private final boolean added;

        public static ProposalLikeData of(Proposal proposal, boolean added) {
            return new ProposalLikeData(proposal, added);
        }
    }

    /** 제안 취소 결과. 반복 요청에도 같은 값을 내린다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalCancelResult {

        private final Long proposalId;
        private final ProposalStatus status;

        public static ProposalCancelResult from(Proposal proposal) {
            return ProposalCancelResult.builder()
                    .proposalId(proposal.getId())
                    .status(proposal.getStatus())
                    .build();
        }
    }

    /** 사장님의 제안 거절 결과. 반복 요청에도 같은 값을 내린다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalRejectResult {

        private final Long proposalId;
        private final ProposalStatus status;

        public static ProposalRejectResult from(Proposal proposal) {
            return ProposalRejectResult.builder()
                    .proposalId(proposal.getId())
                    .status(proposal.getStatus())
                    .build();
        }
    }

    /** 제안과 제안에 선택된 소분류 ID */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalDetailData {

        private final Proposal proposal;
        private final List<Long> specialtyIds;

        public static ProposalDetailData of(Proposal proposal, List<Long> specialtyIds) {
            return new ProposalDetailData(proposal, List.copyOf(specialtyIds));
        }
    }

    /** 탐색 목록의 제안 카드 재료. 제안과 제안에 선택된 소분류 ID */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ExploreProposalData {

        private final Proposal proposal;
        private final List<Long> specialtyIds;

        public static ExploreProposalData of(Proposal proposal, List<Long> specialtyIds) {
            return new ExploreProposalData(proposal, List.copyOf(specialtyIds));
        }
    }

    /** 내가 보낸 제안 목록 결과 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MyProposalListResult {

        private final List<MyProposalResult> proposals;

        public static MyProposalListResult of(List<MyProposalResult> proposals) {
            return new MyProposalListResult(List.copyOf(proposals));
        }
    }

    /** 내가 보낸 제안 카드. 매장은 현재 프로필 값이다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MyProposalResult {

        private final Long proposalId;
        private final String title;
        private final ProposalStatus status;
        private final Integer likeCount;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final String proposedSolution;
        private final ProposalStoreResult store;
        // 결제로 만들어진 의뢰. 결제 전이면 null
        private final Long jobId;
        private final JobStatus jobStatus;
        // 저장된 원본 생성 시각(UTC)
        private final LocalDateTime createdAt;
        // 거절한 주체와 저장된 원본 거절 시각(UTC). 거절되지 않았거나 기록 전에 거절된 제안은 null
        private final ProposalRejectedBy rejectedBy;
        private final LocalDateTime rejectedAt;

        public static MyProposalResult of(Proposal proposal, Owner owner,
                List<SpecialtyCategoryResult> specialtyCategories, Job job) {
            return MyProposalResult.builder()
                    .proposalId(proposal.getId())
                    .title(proposal.getTitle())
                    .status(proposal.getStatus())
                    .jobId(job == null ? null : job.getId())
                    .jobStatus(job == null ? null : job.getStatus())
                    .likeCount(proposal.getLikeCount())
                    .specialtyCategories(specialtyCategories)
                    .proposedSolution(proposal.getProposedSolution())
                    .store(ProposalStoreResult.of(owner))
                    .createdAt(proposal.getCreatedAt())
                    .rejectedBy(proposal.getRejectedBy())
                    .rejectedAt(proposal.getRejectedAt())
                    .build();
        }
    }

    /** 사장님이 받은 제안 목록 결과 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReceivedProposalListResult {

        private final List<ReceivedProposalResult> proposals;

        public static ReceivedProposalListResult of(List<ReceivedProposalResult> proposals) {
            return new ReceivedProposalListResult(List.copyOf(proposals));
        }
    }

    /** 받은 제안 카드. 학생은 현재 프로필 값이다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReceivedProposalResult {

        private final Long proposalId;
        private final String title;
        private final ProposalStatus status;
        private final Integer likeCount;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final String proposedSolution;
        private final ReceivedProposalStudentResult student;
        // 결제로 만들어진 의뢰. 결제 전이면 null
        private final Long jobId;
        private final JobStatus jobStatus;
        // 저장된 원본 생성 시각(UTC)
        private final LocalDateTime createdAt;
        // 거절한 주체와 저장된 원본 거절 시각(UTC). 거절되지 않았거나 기록 전에 거절된 제안은 null
        private final ProposalRejectedBy rejectedBy;
        private final LocalDateTime rejectedAt;

        public static ReceivedProposalResult of(Proposal proposal, Student student, User studentUser,
                List<SpecialtyCategoryResult> specialtyCategories, Job job) {
            return ReceivedProposalResult.builder()
                    .proposalId(proposal.getId())
                    .title(proposal.getTitle())
                    .status(proposal.getStatus())
                    .jobId(job == null ? null : job.getId())
                    .jobStatus(job == null ? null : job.getStatus())
                    .likeCount(proposal.getLikeCount())
                    .specialtyCategories(specialtyCategories)
                    .proposedSolution(proposal.getProposedSolution())
                    .student(ReceivedProposalStudentResult.of(student, studentUser))
                    .createdAt(proposal.getCreatedAt())
                    .rejectedBy(proposal.getRejectedBy())
                    .rejectedAt(proposal.getRejectedAt())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReceivedProposalStudentResult {

        private final Long studentProfileId;
        private final String name;
        private final String studentNumber;
        private final String major;
        // 학생 프로필 사진. 사진이 없으면 null
        private final String profileImageUrl;

        public static ReceivedProposalStudentResult of(Student student, User studentUser) {
            return new ReceivedProposalStudentResult(student.getId(), studentUser.getName(),
                    student.getStudentNumber(), student.getMajor(), student.getProfileImageUrl());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalStoreResult {

        private final Long ownerProfileId;
        private final String storeName;
        private final String storeAddress;
        private final String profileImageUrl;

        public static ProposalStoreResult of(Owner owner) {
            return new ProposalStoreResult(
                    owner.getId(), owner.getStoreName(), owner.getStoreAddress(), owner.getProfileImageUrl());
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalDetailResult {

        private final Long proposalId;
        private final String title;
        private final String storeName;
        // 매장의 현재 프로필 주소. 등록하지 않았으면 null
        private final String storeAddress;
        // 매장 대표 사진(사장님 프로필 사진). 사진이 없으면 null
        private final String storeProfileImageUrl;
        private final Integer likeCount;
        // 조회한 학생 본인의 공감 여부. 학생이 아니거나 학생 프로필이 없으면 false
        private final boolean likedByMe;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final ProposalStudentResult student;
        private final String customerProblem;
        private final String proposedSolution;
        private final String workPlan;
        private final Long proposedFee;
        private final Integer draftDays;
        private final Integer finalDays;
        private final List<String> referenceImageUrls;
        // 저장된 원본 생성 시각(UTC)
        private final LocalDateTime createdAt;
        // 거절한 주체와 저장된 원본 거절 시각(UTC). 거절되지 않았거나 기록 전에 거절된 제안은 null
        private final ProposalRejectedBy rejectedBy;
        private final LocalDateTime rejectedAt;
        private final ProposalStatus status;
        // 결제 전(PENDING)에만 채우는 예상 마감일. 실제 마감일은 결제 승인 시 확정한다
        private final LocalDate estimatedDraftDeadline;
        private final LocalDate estimatedFinalDeadline;
        // 결제로 만들어진 의뢰. 결제 전이면 null
        private final Long jobId;
        // 결제로 확정된 작업 조건. 결제 전이거나 제안의 당사자가 아니면 null
        private final ProposalAgreementResult agreement;

        /**
         * @param likedByMe 조회한 학생 본인의 공감 여부
         * @param today 한국 날짜 기준 오늘. 결제 전 제안의 예상 마감일 계산에 쓴다
         * @param jobId 결제로 만들어진 의뢰 ID, 결제 전이면 null
         * @param agreement 제안의 당사자에게만 내리는 확정 작업 조건, 없으면 null
         */
        public static ProposalDetailResult of(Proposal proposal, String storeName, String storeAddress,
                String storeProfileImageUrl, Student student, User studentUser, BigDecimal averageRating, long completedJobCount,
                List<SpecialtyCategoryResult> specialtyCategories, boolean likedByMe,
                LocalDate today, Long jobId, ProposalAgreementResult agreement) {
            boolean pending = proposal.getStatus() == ProposalStatus.PENDING;
            return ProposalDetailResult.builder()
                    .draftDays(proposal.getDraftDays())
                    .status(proposal.getStatus())
                    .estimatedDraftDeadline(pending ? proposal.draftDeadlineFrom(today) : null)
                    .estimatedFinalDeadline(pending ? proposal.finalDeadlineFrom(today) : null)
                    .jobId(jobId)
                    .agreement(agreement)
                    .proposalId(proposal.getId())
                    .title(proposal.getTitle())
                    .storeName(storeName)
                    .storeAddress(storeAddress)
                    .storeProfileImageUrl(storeProfileImageUrl)
                    .likeCount(proposal.getLikeCount())
                    .likedByMe(likedByMe)
                    .specialtyCategories(specialtyCategories)
                    .student(ProposalStudentResult.of(student, studentUser, averageRating, completedJobCount))
                    .customerProblem(proposal.getCustomerProblem())
                    .proposedSolution(proposal.getProposedSolution())
                    .workPlan(proposal.getWorkPlan())
                    .proposedFee(proposal.getProposedFee())
                    .finalDays(proposal.getFinalDays())
                    .referenceImageUrls(List.copyOf(proposal.getReferenceImageUrls()))
                    .createdAt(proposal.getCreatedAt())
                    .rejectedBy(proposal.getRejectedBy())
                    .rejectedAt(proposal.getRejectedAt())
                    .build();
        }
    }

    /** 결제로 확정된 작업 조건. 제안을 받은 사장님과 제안한 학생에게만 내린다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalAgreementResult {

        private final JobStatus jobStatus;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        // 사장님이 결제 시 남긴 한마디. 입력하지 않았으면 null
        private final String messageToStudent;
        private final Instant paidAt;
        // 학생이 작업을 시작하기 전이면 null
        private final LocalDateTime startedAt;

        public static ProposalAgreementResult of(Job job, Instant paidAt) {
            return ProposalAgreementResult.builder()
                    .jobStatus(job.getStatus())
                    .budget(job.getBudget())
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .revisionCount(job.getRevisionCount())
                    .messageToStudent(job.getAcceptanceMessage())
                    .paidAt(paidAt)
                    .startedAt(job.getStartedAt())
                    .build();
        }
    }

    /** 제안 의뢰의 작업 시작 결과. 마감일은 결제 승인 시 확정한 값 그대로다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalJobStartResult {

        private final Long jobId;
        private final JobStatus jobStatus;
        private final ProposalStatus proposalStatus;
        private final LocalDateTime startedAt;
        private final String chatRoomId;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;

        public static ProposalJobStartResult of(Job job, Proposal proposal, String chatRoomId) {
            return ProposalJobStartResult.builder()
                    .jobId(job.getId())
                    .jobStatus(job.getStatus())
                    .proposalStatus(proposal.getStatus())
                    .startedAt(job.getStartedAt())
                    .chatRoomId(chatRoomId)
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .build();
        }
    }

    /** 제안 의뢰서의 거절 결과. 금액은 환불 처리 시 저장한 값 그대로다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalJobDeclineResult {

        private final Long jobId;
        private final JobStatus jobStatus;
        private final ProposalStatus proposalStatus;
        private final Long paidAmount;
        private final Long studentCompensationAmount;
        private final Long refundAmount;
        private final LocalDateTime declinedAt;

        public static ProposalJobDeclineResult of(Job job, Proposal proposal, RefundedPaymentData refund) {
            return ProposalJobDeclineResult.builder()
                    .jobId(job.getId())
                    .jobStatus(job.getStatus())
                    .proposalStatus(proposal.getStatus())
                    .paidAmount(refund.amount())
                    .studentCompensationAmount(refund.studentCompensationAmount())
                    .refundAmount(refund.refundAmount())
                    .declinedAt(job.getCompletedAt())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalStudentResult {

        private final Long studentProfileId;
        private final String name;
        // 학생 프로필 사진. 사진이 없으면 null
        private final String profileImageUrl;
        private final String major;
        private final String studentNumber;
        private final BigDecimal averageRating;
        private final long completedJobCount;

        public static ProposalStudentResult of(Student student, User studentUser, BigDecimal averageRating,
                long completedJobCount) {
            return new ProposalStudentResult(student.getId(), studentUser.getName(), student.getProfileImageUrl(),
                    student.getMajor(), student.getStudentNumber(), averageRating, completedJobCount);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyCategoryResult {

        private final Long id;
        private final String name;
        private final List<SpecialtyResult> specialties;

        public static SpecialtyCategoryResult of(Long id, String name, List<SpecialtyResult> specialties) {
            return new SpecialtyCategoryResult(id, name, specialties);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyResult {

        private final Long id;
        private final String name;

        public static SpecialtyResult of(Long id, String name) {
            return new SpecialtyResult(id, name);
        }
    }
}
