package com.gakkum.backend.application.student.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.application.payment.dto.SettlementHistoryResponse;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentCertificateResult;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentMeResult;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentReceivedReviewResult;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentSpecialtyCategoryResult;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentSpecialtyResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentMeResponse {

    private final Long studentProfileId;
    private final String profileImageUrl;
    private final String name;
    private final String university;
    private final String studentNumber;
    private final String major;
    private final String introduction;
    private final String portfolioUrl;
    private final Long proposalCount;
    private final Long completedJobCount;
    private final Integer penaltyCount;
    private final BigDecimal averageRating;
    private final List<SpecialtyCategory> specialtyCategories;
    private final List<Certificate> certificates;
    private final Long reviewCount;
    private final List<Review> reviews;
    // 정산 내역 조회와 같은 항목 형식
    private final List<SettlementHistoryResponse.Settlement> settlements;

    public static StudentMeResponse from(StudentMeResult result) {
        return StudentMeResponse.builder()
                .studentProfileId(result.getStudentProfileId())
                .profileImageUrl(result.getProfileImageUrl())
                .name(result.getName())
                .university(result.getUniversity())
                .studentNumber(result.getStudentNumber())
                .major(result.getMajor())
                .introduction(result.getIntroduction())
                .portfolioUrl(result.getPortfolioUrl())
                .proposalCount(result.getProposalCount())
                .completedJobCount(result.getCompletedJobCount())
                .penaltyCount(result.getPenaltyCount())
                .averageRating(result.getAverageRating())
                .specialtyCategories(result.getSpecialtyCategories().stream().map(SpecialtyCategory::from).toList())
                .certificates(result.getCertificates().stream().map(Certificate::from).toList())
                .reviewCount(result.getReviewCount())
                .reviews(result.getReviews().stream().map(Review::from).toList())
                .settlements(result.getSettlements().stream()
                        .map(SettlementHistoryResponse.Settlement::from)
                        .toList())
                .build();
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SpecialtyCategory {

        private final Long id;
        private final String name;
        private final List<Specialty> specialties;

        public static SpecialtyCategory from(StudentSpecialtyCategoryResult result) {
            return SpecialtyCategory.builder()
                    .id(result.getId())
                    .name(result.getName())
                    .specialties(result.getSpecialties().stream().map(Specialty::from).toList())
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Specialty {

        private final Long id;
        private final String name;

        public static Specialty from(StudentSpecialtyResult result) {
            return Specialty.builder()
                    .id(result.getId())
                    .name(result.getName())
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Certificate {

        private final String certificateName;
        private final Integer acquiredYear;

        public static Certificate from(StudentCertificateResult result) {
            return Certificate.builder()
                    .certificateName(result.getCertificateName())
                    .acquiredYear(result.getAcquiredYear())
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Review {

        private final String jobTitle;
        private final String storeName;
        private final List<SpecialtyCategory> specialtyCategories;
        private final Integer rating;
        private final String content;
        private final LocalDate createdAt;

        public static Review from(StudentReceivedReviewResult result) {
            return Review.builder()
                    .jobTitle(result.getJobTitle())
                    .storeName(result.getStoreName())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(SpecialtyCategory::from)
                            .toList())
                    .rating(result.getRating())
                    .content(result.getContent())
                    .createdAt(result.getCreatedAt())
                    .build();
        }
    }
}
