package com.gakkum.backend.application.proposal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalAgreementResult;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalStudentResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.SpecialtyResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProposalDetailResponse {

    private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

    private final Long proposalId;
    private final String title;
    private final String storeName;
    // 매장의 현재 프로필 주소. 등록하지 않았으면 null
    private final String storeAddress;
    private final Integer likeCount;
    // 학생이 아닌 사용자에게도 false로 항상 내린다
    private final boolean likedByMe;
    private final List<SpecialtyCategory> specialtyCategories;
    private final ProposalStudent student;
    private final String customerProblem;
    private final String proposedSolution;
    private final String workPlan;
    private final Long proposedFee;
    private final Integer draftDays;
    private final Integer finalDays;
    private final List<String> referenceImageUrls;
    // 한국 시각. 오프셋 없이 내린다
    private final LocalDateTime createdAt;
    private final ProposalStatus status;
    // 결제 전(PENDING)에만 내리는 한국 날짜 기준 예상 마감일
    private final LocalDate estimatedDraftDeadline;
    private final LocalDate estimatedFinalDeadline;
    // 결제로 만들어진 의뢰. 결제 전이면 null
    private final Long jobId;
    // 결제로 확정된 작업 조건. 결제 전이거나 제안의 당사자가 아니면 null
    private final Agreement agreement;

    public static ProposalDetailResponse from(ProposalDetailResult result) {
        return ProposalDetailResponse.builder()
                .proposalId(result.getProposalId())
                .title(result.getTitle())
                .storeName(result.getStoreName())
                .storeAddress(result.getStoreAddress())
                .likeCount(result.getLikeCount())
                .likedByMe(result.isLikedByMe())
                .specialtyCategories(result.getSpecialtyCategories().stream()
                        .map(SpecialtyCategory::from)
                        .toList())
                .student(ProposalStudent.from(result.getStudent()))
                .customerProblem(result.getCustomerProblem())
                .proposedSolution(result.getProposedSolution())
                .workPlan(result.getWorkPlan())
                .proposedFee(result.getProposedFee())
                .draftDays(result.getDraftDays())
                .finalDays(result.getFinalDays())
                .referenceImageUrls(result.getReferenceImageUrls())
                .createdAt(toKoreaTime(result.getCreatedAt()))
                .status(result.getStatus())
                .estimatedDraftDeadline(result.getEstimatedDraftDeadline())
                .estimatedFinalDeadline(result.getEstimatedFinalDeadline())
                .jobId(result.getJobId())
                .agreement(result.getAgreement() == null ? null : Agreement.from(result.getAgreement()))
                .build();
    }

    /** UTC로 저장된 시각을 한국 시각으로 바꾼다. 제안 목록 응답과 함께 쓴다. */
    static LocalDateTime toKoreaTime(LocalDateTime utc) {
        return utc == null ? null
                : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(RESPONSE_ZONE).toLocalDateTime();
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Agreement {

        private final JobStatus jobStatus;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final String messageToStudent;
        private final Instant paidAt;
        private final LocalDateTime startedAt;

        public static Agreement from(ProposalAgreementResult result) {
            return Agreement.builder()
                    .jobStatus(result.getJobStatus())
                    .budget(result.getBudget())
                    .draftDeadline(result.getDraftDeadline())
                    .finalDeadline(result.getFinalDeadline())
                    .revisionCount(result.getRevisionCount())
                    .messageToStudent(result.getMessageToStudent())
                    .paidAt(result.getPaidAt())
                    .startedAt(result.getStartedAt())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalStudent {

        private final Long studentProfileId;
        private final String name;
        private final String major;
        // 전체 학번 대신 입학년도 뒤 두 자리만 전달한다 (2024402001 → "24")
        private final String studentNumber;
        private final BigDecimal averageRating;
        private final long completedJobCount;

        public static ProposalStudent from(ProposalStudentResult result) {
            String studentNumber = result.getStudentNumber();
            return new ProposalStudent(result.getStudentProfileId(), result.getName(), result.getMajor(),
                    studentNumber == null ? null : studentNumber.substring(2, 4),
                    result.getAverageRating(), result.getCompletedJobCount());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyCategory {

        private final Long id;
        private final String name;
        private final List<Specialty> specialties;

        public static SpecialtyCategory from(SpecialtyCategoryResult result) {
            return new SpecialtyCategory(
                    result.getId(),
                    result.getName(),
                    result.getSpecialties().stream().map(Specialty::from).toList());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Specialty {

        private final Long id;
        private final String name;

        public static Specialty from(SpecialtyResult result) {
            return new Specialty(result.getId(), result.getName());
        }
    }
}
