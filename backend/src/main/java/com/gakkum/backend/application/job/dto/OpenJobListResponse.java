package com.gakkum.backend.application.job.dto;

import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OpenJobListResponse {

    private final List<JobResponse> jobs;

    public static OpenJobListResponse from(OpenJobListResult result) {
        return new OpenJobListResponse(result.getJobs().stream()
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
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final Integer applicantCount;

        public static JobResponse from(OpenJobResult result) {
            return JobResponse.builder()
                    .jobId(result.getJobId())
                    .title(result.getTitle())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(SpecialtyCategoryResponse::from)
                            .toList())
                    .draftDeadline(result.getDraftDeadline())
                    .finalDeadline(result.getFinalDeadline())
                    .revisionCount(result.getRevisionCount())
                    .applicantCount(result.getApplicantCount())
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
