package com.gakkum.backend.application.job.dto;

import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobResult;
import com.gakkum.backend.domain.job.entity.JobProgressStage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentMatchedJobListResponse {

    private final List<JobResponse> jobs;

    public static StudentMatchedJobListResponse from(StudentMatchedJobListResult result) {
        return new StudentMatchedJobListResponse(result.getJobs().stream()
                .map(JobResponse::from)
                .toList());
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobResponse {

        private final Long jobId;
        private final String title;
        private final List<SpecialtyCategoryResponse> specialtyCategories;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final String submissionType;
        private final String reviewStatus;
        private final JobProgressStage progressStage;

        public static JobResponse from(StudentMatchedJobResult result) {
            return JobResponse.builder()
                    .jobId(result.getJobId())
                    .title(result.getTitle())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(SpecialtyCategoryResponse::from)
                            .toList())
                    .budget(result.getBudget())
                    .draftDeadline(result.getDraftDeadline())
                    .finalDeadline(result.getFinalDeadline())
                    .revisionCount(result.getRevisionCount())
                    .submissionType(result.getSubmissionType())
                    .reviewStatus(result.getReviewStatus())
                    .progressStage(result.getProgressStage())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyCategoryResponse {

        private final Long id;
        private final String name;
        private final List<SpecialtyResponse> specialties;

        public static SpecialtyCategoryResponse from(SpecialtyCategoryResult result) {
            return new SpecialtyCategoryResponse(
                    result.getId(),
                    result.getName(),
                    result.getSpecialties().stream().map(SpecialtyResponse::from).toList());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyResponse {

        private final Long id;
        private final String name;

        public static SpecialtyResponse from(SpecialtyResult result) {
            return new SpecialtyResponse(result.getId(), result.getName());
        }
    }
}
