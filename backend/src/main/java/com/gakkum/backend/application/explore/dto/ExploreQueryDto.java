package com.gakkum.backend.application.explore.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.proposal.entity.Proposal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class ExploreQueryDto {

    private ExploreQueryDto() {
    }

    /** 정렬된 카드 한 페이지. 다음 페이지가 없으면 nextCursor는 null이다. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ExploreResult {

        private final List<ExploreItemResult> items;
        private final String nextCursor;
        private final boolean hasNext;

        public static ExploreResult of(List<ExploreItemResult> items, String nextCursor) {
            return new ExploreResult(List.copyOf(items), nextCursor, nextCursor != null);
        }
    }

    public interface ExploreItemResult {

        ExploreItemType getType();

        List<SpecialtyCategoryResult> getSpecialtyCategories();
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalCardResult implements ExploreItemResult {

        private final ExploreItemType type;
        private final Long proposalId;
        private final String title;
        private final String storeName;
        private final Integer likeCount;
        private final List<SpecialtyCategoryResult> specialtyCategories;

        public static ProposalCardResult of(
                Proposal proposal, String storeName, List<SpecialtyCategoryResult> specialtyCategories) {
            return ProposalCardResult.builder()
                    .type(ExploreItemType.PROPOSAL)
                    .proposalId(proposal.getId())
                    .title(proposal.getTitle())
                    .storeName(storeName)
                    .likeCount(proposal.getLikeCount())
                    .specialtyCategories(specialtyCategories)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobCardResult implements ExploreItemResult {

        private final ExploreItemType type;
        private final Long jobId;
        private final String storeName;
        private final String title;
        private final JobProgressStage progressStage;
        private final JobStatus status;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final List<SpecialtyCategoryResult> specialtyCategories;

        public static JobCardResult of(Job job, JobProgressStage progressStage, String storeName,
                List<SpecialtyCategoryResult> specialtyCategories) {
            return JobCardResult.builder()
                    .type(ExploreItemType.JOB)
                    .jobId(job.getId())
                    .storeName(storeName)
                    .title(job.getTitle())
                    .progressStage(progressStage)
                    .status(job.getStatus())
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .specialtyCategories(specialtyCategories)
                    .build();
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

    /** 정렬된 매장 한 페이지. 다음 페이지가 없으면 nextCursor는 null이다. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StoreExploreResult {

        private final List<StoreItemResult> items;
        private final String nextCursor;
        private final boolean hasNext;

        public static StoreExploreResult of(List<StoreItemResult> items, String nextCursor) {
            return new StoreExploreResult(List.copyOf(items), nextCursor, nextCursor != null);
        }
    }

    /** 사장님 프로필 하나를 매장 하나로 본다. createdAt은 개업일이 아닌 프로필 생성 시각이다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StoreItemResult {

        private final String storeName;
        private final String profileImageUrl;
        private final BusinessCategoryResult businessCategory;
        private final String storeAddress;
        private final Long ownerProfileId;
        private final LocalDateTime createdAt;

        public static StoreItemResult of(Owner owner, BusinessCategoryResult businessCategory) {
            return StoreItemResult.builder()
                    .storeName(owner.getStoreName())
                    .profileImageUrl(owner.getProfileImageUrl())
                    .businessCategory(businessCategory)
                    .storeAddress(owner.getStoreAddress())
                    .ownerProfileId(owner.getId())
                    .createdAt(owner.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class BusinessCategoryResult {

        private final Long id;
        private final String name;

        public static BusinessCategoryResult of(Long id, String name) {
            return new BusinessCategoryResult(id, name);
        }
    }
}
