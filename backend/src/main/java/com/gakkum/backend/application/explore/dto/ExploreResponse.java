package com.gakkum.backend.application.explore.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ExploreItemResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ExploreResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.JobCardResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ProposalCardResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.SpecialtyResult;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 제안·의뢰 카드를 정렬된 한 배열로 내린다. 각 카드의 type으로 종류를 구분한다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExploreResponse {

    private final List<Item> items;
    private final String nextCursor;
    private final boolean hasNext;

    public static ExploreResponse from(ExploreResult result) {
        return new ExploreResponse(
                result.getItems().stream().map(ExploreResponse::toItem).toList(),
                result.getNextCursor(),
                result.isHasNext());
    }

    private static Item toItem(ExploreItemResult result) {
        if (result instanceof ProposalCardResult proposal) {
            return ProposalCard.from(proposal);
        }
        if (result instanceof JobCardResult job) {
            return JobCard.from(job);
        }
        throw new IllegalStateException("Unknown explore item: " + result.getClass());
    }

    public interface Item {

        ExploreItemType getType();
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProposalCard implements Item {

        private final ExploreItemType type;
        private final Long proposalId;
        private final String title;
        private final String storeName;
        private final Integer likeCount;
        private final List<SpecialtyCategory> specialtyCategories;

        public static ProposalCard from(ProposalCardResult result) {
            return ProposalCard.builder()
                    .type(result.getType())
                    .proposalId(result.getProposalId())
                    .title(result.getTitle())
                    .storeName(result.getStoreName())
                    .likeCount(result.getLikeCount())
                    .specialtyCategories(SpecialtyCategory.from(result.getSpecialtyCategories()))
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobCard implements Item {

        private final ExploreItemType type;
        private final Long jobId;
        private final String storeName;
        private final String title;
        private final JobProgressStage progressStage;
        private final JobStatus status;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Long budget;
        // 학생이 아닌 사용자에게는 필드를 내리지 않는다
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Boolean applied;
        private final List<SpecialtyCategory> specialtyCategories;

        public static JobCard from(JobCardResult result) {
            return JobCard.builder()
                    .type(result.getType())
                    .jobId(result.getJobId())
                    .storeName(result.getStoreName())
                    .title(result.getTitle())
                    .progressStage(result.getProgressStage())
                    .status(result.getStatus())
                    .draftDeadline(result.getDraftDeadline())
                    .finalDeadline(result.getFinalDeadline())
                    .budget(result.getBudget())
                    .applied(result.getApplied())
                    .specialtyCategories(SpecialtyCategory.from(result.getSpecialtyCategories()))
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyCategory {

        private final Long id;
        private final String name;
        private final List<Specialty> specialties;

        static List<SpecialtyCategory> from(List<SpecialtyCategoryResult> results) {
            return results.stream()
                    .map(result -> new SpecialtyCategory(
                            result.getId(),
                            result.getName(),
                            result.getSpecialties().stream().map(Specialty::from).toList()))
                    .toList();
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
