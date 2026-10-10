package com.gakkum.backend.application.job.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.application.job.dto.JobListResponse.SpecialtyCategory;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicantResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicationJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicationListResult;
import com.gakkum.backend.global.response.AdmissionYear;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobApplicationListResponse {

    private final Job job;
    private final Integer applicantCount;
    private final List<Applicant> applicants;

    public static JobApplicationListResponse from(JobApplicationListResult result) {
        return JobApplicationListResponse.builder()
                .job(Job.from(result.getJob()))
                .applicantCount(result.getApplicantCount())
                .applicants(result.getApplicants().stream()
                        .map(Applicant::from)
                        .toList())
                .build();
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Job {

        private final Long jobId;
        private final String title;
        private final List<SpecialtyCategory> specialtyCategories;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;

        public static Job from(JobApplicationJobResult result) {
            return Job.builder()
                    .jobId(result.getJobId())
                    .title(result.getTitle())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(SpecialtyCategory::from)
                            .toList())
                    .budget(result.getBudget())
                    .draftDeadline(result.getDraftDeadline())
                    .finalDeadline(result.getFinalDeadline())
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Applicant {

        private final Long jobApplicationId;
        private final Long studentProfileId;
        private final String profileImageUrl;
        private final String name;
        // 학번 전체 대신 입학연도 두 자리만 내린다: 2024402001 → "24". 학번이 없거나 네 자리보다 짧으면 null
        private final String studentNumber;
        private final String major;
        private final BigDecimal averageRating;
        private final Long completedJobCount;
        private final List<SpecialtyCategory> specialtyCategories;
        private final String summary;
        private final String workPlan;
        private final String deliveryMethod;

        public static Applicant from(JobApplicantResult result) {
            return Applicant.builder()
                    .jobApplicationId(result.getJobApplicationId())
                    .studentProfileId(result.getStudentProfileId())
                    .profileImageUrl(result.getProfileImageUrl())
                    .name(result.getName())
                    .studentNumber(AdmissionYear.from(result.getStudentNumber()))
                    .major(result.getMajor())
                    .averageRating(result.getAverageRating())
                    .completedJobCount(result.getCompletedJobCount())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(SpecialtyCategory::from)
                            .toList())
                    .summary(result.getSummary())
                    .workPlan(result.getWorkPlan())
                    .deliveryMethod(result.getDeliveryMethod())
                    .build();
        }
    }
}
