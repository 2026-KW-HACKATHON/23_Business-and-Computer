package com.gakkum.backend.application.job.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedWorkerResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyCategoryResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ClosedJobListResponse {

    private final List<JobResponse> jobs;

    public static ClosedJobListResponse from(ClosedJobListResult result) {
        return new ClosedJobListResponse(result.getJobs().stream()
                .map(JobResponse::from)
                .toList());
    }

    @JsonValue
    public List<JobResponse> getJobs() {
        return jobs;
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobResponse {

        private final Long jobId;
        private final String title;
        private final List<SpecialtyCategoryResponse> specialtyCategories;
        private final MatchedWorkerResponse matchedWorker;
        private final LocalDate completedAt;

        public static JobResponse from(ClosedJobResult result) {
            return JobResponse.builder()
                    .jobId(result.getJobId())
                    .title(result.getTitle())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(SpecialtyCategoryResponse::from)
                            .toList())
                    .matchedWorker(MatchedWorkerResponse.from(result.getMatchedWorker()))
                    .completedAt(result.getCompletedAt())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyCategoryResponse {

        private final Long id;
        private final String name;

        public static SpecialtyCategoryResponse from(SpecialtyCategoryResult result) {
            return new SpecialtyCategoryResponse(result.getId(), result.getName());
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MatchedWorkerResponse {

        private final Long studentProfileId;
        private final String name;

        public static MatchedWorkerResponse from(MatchedWorkerResult result) {
            return new MatchedWorkerResponse(result.getStudentProfileId(), result.getName());
        }
    }
}
