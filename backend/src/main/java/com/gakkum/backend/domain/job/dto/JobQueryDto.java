package com.gakkum.backend.domain.job.dto;

import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.domain.job.entity.Job;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class JobQueryDto {

    private JobQueryDto() {
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OpenJobData {

        private final Job job;
        private final List<Long> specialtyIds;
        private final Integer applicantCount;

        public static OpenJobData of(Job job, List<Long> specialtyIds, Integer applicantCount) {
            return new OpenJobData(job, specialtyIds, applicantCount);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OpenJobListResult {

        private final List<OpenJobResult> jobs;

        public static OpenJobListResult of(List<OpenJobResult> jobs) {
            return new OpenJobListResult(jobs);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OpenJobResult {

        private final Long jobId;
        private final String title;
        private final List<SpecialtyCategoryResult> specialtyCategories;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final Integer applicantCount;

        public static OpenJobResult of(OpenJobData data, List<SpecialtyCategoryResult> specialtyCategories) {
            Job job = data.getJob();
            return OpenJobResult.builder()
                    .jobId(job.getId())
                    .title(job.getTitle())
                    .specialtyCategories(specialtyCategories)
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .revisionCount(job.getRevisionCount())
                    .applicantCount(data.getApplicantCount())
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
}
