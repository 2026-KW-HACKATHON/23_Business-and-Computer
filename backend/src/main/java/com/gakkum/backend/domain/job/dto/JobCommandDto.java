package com.gakkum.backend.domain.job.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class JobCommandDto {

    private JobCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetOpenJobsCommand {

        private final Long ownerProfileId;

        public static GetOpenJobsCommand of(Long ownerProfileId) {
            return GetOpenJobsCommand.builder()
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetMatchedJobsCommand {

        private final Long ownerProfileId;

        public static GetMatchedJobsCommand of(Long ownerProfileId) {
            return GetMatchedJobsCommand.builder()
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CreateJobCommand {

        private final Long ownerProfileId;
        private final List<Long> specialtyIds;
        private final String title;
        private final String description;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;

        public static CreateJobCommand of(
                Long ownerProfileId,
                List<Long> specialtyIds,
                String title,
                String description,
                Long budget,
                LocalDate draftDeadline,
                LocalDate finalDeadline,
                Integer revisionCount) {
            return CreateJobCommand.builder()
                    .ownerProfileId(ownerProfileId)
                    .specialtyIds(specialtyIds)
                    .title(title)
                    .description(description)
                    .budget(budget)
                    .draftDeadline(draftDeadline)
                    .finalDeadline(finalDeadline)
                    .revisionCount(revisionCount)
                    .build();
        }
    }
}
