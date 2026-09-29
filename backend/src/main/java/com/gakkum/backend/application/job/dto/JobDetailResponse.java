package com.gakkum.backend.application.job.dto;

import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.application.job.dto.JobListResponse.SpecialtyCategory;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobDetailResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobResultResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.WorkHistoryResult;
import com.gakkum.backend.domain.job.entity.JobProgressStage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class JobDetailResponse {

    private JobDetailResponse() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Detail {

        private final Long id;
        private final String title;
        private final String description;
        private final Long budget;
        private final List<SpecialtyCategory> specialtyCategories;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final JobProgressStage progressStage;

        public static Detail from(JobDetailResult result) {
            return Detail.builder()
                    .id(result.getId())
                    .title(result.getTitle())
                    .description(result.getDescription())
                    .budget(result.getBudget())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(SpecialtyCategory::from)
                            .toList())
                    .draftDeadline(result.getDraftDeadline())
                    .finalDeadline(result.getFinalDeadline())
                    .revisionCount(result.getRevisionCount())
                    .progressStage(result.getProgressStage())
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Result {

        private final Long jobId;
        private final String title;
        private final String studentName;
        private final LocalDate completedAt;
        private final boolean normalCompleted;
        private final Long workFee;
        private final List<String> fileUrls;
        private final String message;
        private final List<WorkHistory> workHistory;

        public static Result from(JobResultResult result) {
            return Result.builder()
                    .jobId(result.getJobId())
                    .title(result.getTitle())
                    .studentName(result.getStudentName())
                    .completedAt(result.getCompletedAt())
                    .normalCompleted(result.isNormalCompleted())
                    .workFee(result.getWorkFee())
                    .fileUrls(result.getFileUrls())
                    .message(result.getMessage())
                    .workHistory(result.getWorkHistory().stream()
                            .map(WorkHistory::from)
                            .toList())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class WorkHistory {

        private final String type;
        private final LocalDate date;

        public static WorkHistory from(WorkHistoryResult result) {
            return new WorkHistory(result.getType().name(), result.getDate());
        }
    }
}
