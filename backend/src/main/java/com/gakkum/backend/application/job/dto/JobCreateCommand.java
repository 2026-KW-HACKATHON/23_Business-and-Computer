package com.gakkum.backend.application.job.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobCreateCommand {

    private final List<Long> specialtyIds;
    private final String title;
    private final String description;
    private final Long budget;
    private final LocalDate draftDeadline;
    private final LocalDate finalDeadline;
    private final Integer revisionCount;

    public static JobCreateCommand of(
            List<Long> specialtyIds,
            String title,
            String description,
            Long budget,
            LocalDate draftDeadline,
            LocalDate finalDeadline,
            Integer revisionCount) {
        return JobCreateCommand.builder()
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
