package com.gakkum.backend.application.job.dto;

import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.application.job.dto.JobListResponse.SpecialtyCategory;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ApplicantCertificateResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ApplicantReviewResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ApplicantStudentResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicantProfileResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobApplicantProfileResponse {

    private final StudentInfo student;
    private final Long proposalCount;
    private final Long completedJobCount;
    private final List<SpecialtyCategory> specialtyCategories;
    private final List<Certificate> certificates;
    private final String portfolioUrl;
    private final Integer penaltyCount;
    private final Integer reviewCount;
    private final List<Review> reviews;

    public static JobApplicantProfileResponse from(JobApplicantProfileResult result) {
        return JobApplicantProfileResponse.builder()
                .student(StudentInfo.from(result.getStudent()))
                .proposalCount(result.getProposalCount())
                .completedJobCount(result.getCompletedJobCount())
                .specialtyCategories(result.getSpecialtyCategories().stream()
                        .map(SpecialtyCategory::from)
                        .toList())
                .certificates(result.getCertificates().stream()
                        .map(Certificate::from)
                        .toList())
                .portfolioUrl(result.getPortfolioUrl())
                .penaltyCount(result.getPenaltyCount())
                .reviewCount(result.getReviewCount())
                .reviews(result.getReviews().stream()
                        .map(Review::from)
                        .toList())
                .build();
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentInfo {

        private final Long studentProfileId;
        private final String name;
        // 학생 프로필 사진. 사진이 없으면 null
        private final String profileImageUrl;
        private final String university;
        private final String major;
        private final String studentNumber;

        public static StudentInfo from(ApplicantStudentResult result) {
            return StudentInfo.builder()
                    .studentProfileId(result.getStudentProfileId())
                    .name(result.getName())
                    .profileImageUrl(result.getProfileImageUrl())
                    .university(result.getUniversity())
                    .major(result.getMajor())
                    .studentNumber(result.getStudentNumber())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Certificate {

        private final String certificateName;
        private final Integer acquiredYear;

        public static Certificate from(ApplicantCertificateResult result) {
            return new Certificate(result.getCertificateName(), result.getAcquiredYear());
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Review {

        private final String storeName;
        private final String jobTitle;
        private final String content;
        private final Integer rating;
        private final LocalDate createdAt;

        public static Review from(ApplicantReviewResult result) {
            return Review.builder()
                    .storeName(result.getStoreName())
                    .jobTitle(result.getJobTitle())
                    .content(result.getContent())
                    .rating(result.getRating())
                    .createdAt(result.getCreatedAt())
                    .build();
        }
    }
}
