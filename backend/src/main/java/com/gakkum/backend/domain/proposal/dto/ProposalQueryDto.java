package com.gakkum.backend.domain.proposal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.proposal.entity.Proposal;
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

        public static MyProposalResult of(Proposal proposal, Owner owner,
                List<SpecialtyCategoryResult> specialtyCategories) {
            return MyProposalResult.builder()
                    .proposalId(proposal.getId())
                    .title(proposal.getTitle())
                    .status(proposal.getStatus())
                    .likeCount(proposal.getLikeCount())
                    .specialtyCategories(specialtyCategories)
                    .proposedSolution(proposal.getProposedSolution())
                    .store(ProposalStoreResult.of(owner))
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

        public static ReceivedProposalResult of(Proposal proposal, Student student, User studentUser,
                List<SpecialtyCategoryResult> specialtyCategories) {
            return ReceivedProposalResult.builder()
                    .proposalId(proposal.getId())
                    .title(proposal.getTitle())
                    .status(proposal.getStatus())
                    .likeCount(proposal.getLikeCount())
                    .specialtyCategories(specialtyCategories)
                    .proposedSolution(proposal.getProposedSolution())
                    .student(ReceivedProposalStudentResult.of(student, studentUser))
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

        public static ReceivedProposalStudentResult of(Student student, User studentUser) {
            return new ReceivedProposalStudentResult(
                    student.getId(), studentUser.getName(), student.getStudentNumber(), student.getMajor());
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

        public static ProposalDetailResult of(Proposal proposal, String storeName, Student student, User studentUser,
                BigDecimal averageRating, long completedJobCount,
                List<SpecialtyCategoryResult> specialtyCategories) {
            return ProposalDetailResult.builder()
                    .proposalId(proposal.getId())
                    .title(proposal.getTitle())
                    .storeName(storeName)
                    .likeCount(proposal.getLikeCount())
                    .specialtyCategories(specialtyCategories)
                    .student(ProposalStudentResult.of(student, studentUser, averageRating, completedJobCount))
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
        private final BigDecimal averageRating;
        private final long completedJobCount;

        public static ProposalStudentResult of(Student student, User studentUser, BigDecimal averageRating,
                long completedJobCount) {
            return new ProposalStudentResult(student.getId(), studentUser.getName(), student.getMajor(),
                    student.getStudentNumber(), averageRating, completedJobCount);
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
