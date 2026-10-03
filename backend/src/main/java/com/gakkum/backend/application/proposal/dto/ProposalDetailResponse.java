package com.gakkum.backend.application.proposal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    private final Long proposalId;
    private final String title;
    private final String storeName;
    private final Integer likeCount;
    private final List<SpecialtyCategory> specialtyCategories;
    private final ProposalStudent student;
    private final String customerProblem;
    private final String proposedSolution;
    private final String workPlan;
    private final Long proposedFee;
    private final Integer finalDays;
    private final List<String> referenceImageUrls;
    private final LocalDateTime createdAt;

    public static ProposalDetailResponse from(ProposalDetailResult result) {
        return ProposalDetailResponse.builder()
                .proposalId(result.getProposalId())
                .title(result.getTitle())
                .storeName(result.getStoreName())
                .likeCount(result.getLikeCount())
                .specialtyCategories(result.getSpecialtyCategories().stream()
                        .map(SpecialtyCategory::from)
                        .toList())
                .student(ProposalStudent.from(result.getStudent()))
                .customerProblem(result.getCustomerProblem())
                .proposedSolution(result.getProposedSolution())
                .workPlan(result.getWorkPlan())
                .proposedFee(result.getProposedFee())
                .finalDays(result.getFinalDays())
                .referenceImageUrls(result.getReferenceImageUrls())
                .createdAt(result.getCreatedAt())
                .build();
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalStudent {

        private final Long studentProfileId;
        private final String name;
        private final String major;
        private final String studentNumber;
        private final BigDecimal averageRating;
        private final long completedJobCount;

        public static ProposalStudent from(ProposalStudentResult result) {
            return new ProposalStudent(result.getStudentProfileId(), result.getName(), result.getMajor(),
                    result.getStudentNumber(), result.getAverageRating(), result.getCompletedJobCount());
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
