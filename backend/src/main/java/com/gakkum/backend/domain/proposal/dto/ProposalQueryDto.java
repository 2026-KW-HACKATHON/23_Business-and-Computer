package com.gakkum.backend.domain.proposal.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.gakkum.backend.domain.proposal.entity.Proposal;
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

    /** 사장님이 받은 제안과 제안에 선택된 소분류 ID */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReceivedProposalData {

        private final Proposal proposal;
        private final List<Long> specialtyIds;

        public static ReceivedProposalData of(Proposal proposal, List<Long> specialtyIds) {
            return new ReceivedProposalData(proposal, List.copyOf(specialtyIds));
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReceivedProposalResult {

        private final Long proposalId;
        private final String title;
        private final Integer likeCount;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final ProposalStudentResult student;
        private final String customerProblem;
        private final String proposedSolution;
        private final String workPlan;
        private final Long proposedFee;
        private final Integer finalDays;
        private final List<String> referenceImageUrls;
        private final LocalDateTime createdAt;

        public static ReceivedProposalResult of(Proposal proposal, Student student, User studentUser,
                List<SpecialtyCategoryResult> specialtyCategories) {
            return ReceivedProposalResult.builder()
                    .proposalId(proposal.getId())
                    .title(proposal.getTitle())
                    .likeCount(proposal.getLikeCount())
                    .specialtyCategories(specialtyCategories)
                    .student(ProposalStudentResult.of(student, studentUser))
                    .customerProblem(proposal.getCustomerProblem())
                    .proposedSolution(proposal.getProposedSolution())
                    .workPlan(proposal.getWorkPlan())
                    .proposedFee(proposal.getProposedFee())
                    .finalDays(proposal.getFinalDays())
                    .referenceImageUrls(List.copyOf(proposal.getReferenceImageUrls()))
                    .createdAt(proposal.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalStudentResult {

        private final Long studentProfileId;
        private final String name;
        private final String major;
        private final String studentNumber;

        public static ProposalStudentResult of(Student student, User studentUser) {
            return new ProposalStudentResult(
                    student.getId(), studentUser.getName(), student.getMajor(), student.getStudentNumber());
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
