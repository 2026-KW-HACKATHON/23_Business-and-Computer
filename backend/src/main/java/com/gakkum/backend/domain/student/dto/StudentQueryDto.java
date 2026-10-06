package com.gakkum.backend.domain.student.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.domain.certificate.entity.StudentCertificate;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryItemResult;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.user.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class StudentQueryDto {

    private StudentQueryDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentMeResult {

        private final Long studentProfileId;
        private final String profileImageUrl;
        private final String name;
        private final String university;
        // 학번 전체가 아닌 입학연도 두 자리
        private final String studentNumber;
        private final String introduction;
        private final Long proposalCount;
        private final Long completedJobCount;
        private final Integer penaltyCount;
        private final BigDecimal averageRating;
        private final List<StudentSpecialtyCategoryResult> specialtyCategories;
        private final List<StudentCertificateResult> certificates;
        private final Long reviewCount;
        private final List<StudentReceivedReviewResult> reviews;
        private final List<SettlementHistoryItemResult> settlements;

        /**
         * 리뷰 수와 평균 별점은 반환하는 리뷰 목록이 아닌 학생이 받은 전체 리뷰 기준이다.
         * @param admissionYear 학번에서 뽑은 입학연도 두 자리
         */
        public static StudentMeResult of(
                Student student,
                User user,
                String admissionYear,
                long proposalCount,
                long completedJobCount,
                BigDecimal averageRating,
                List<StudentSpecialtyCategoryResult> specialtyCategories,
                List<StudentCertificate> certificates,
                long reviewCount,
                List<StudentReceivedReviewResult> reviews,
                List<SettlementHistoryItemResult> settlements) {
            return StudentMeResult.builder()
                    .studentProfileId(student.getId())
                    .profileImageUrl(student.getProfileImageUrl())
                    .name(user.getName())
                    .university(student.getUniversity())
                    .studentNumber(admissionYear)
                    .introduction(student.getIntroduction())
                    .proposalCount(proposalCount)
                    .completedJobCount(completedJobCount)
                    .penaltyCount(student.getPenaltyCount())
                    .averageRating(averageRating)
                    .specialtyCategories(List.copyOf(specialtyCategories))
                    .certificates(certificates.stream()
                            .map(StudentCertificateResult::from)
                            .toList())
                    .reviewCount(reviewCount)
                    .reviews(List.copyOf(reviews))
                    .settlements(List.copyOf(settlements))
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentSpecialtyCategoryResult {

        private final Long id;
        private final String name;
        private final List<StudentSpecialtyResult> specialties;

        public static StudentSpecialtyCategoryResult of(Long id, String name, List<StudentSpecialtyResult> specialties) {
            return new StudentSpecialtyCategoryResult(id, name, specialties);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentSpecialtyResult {

        private final Long id;
        private final String name;

        public static StudentSpecialtyResult of(Long id, String name) {
            return new StudentSpecialtyResult(id, name);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentCertificateResult {

        private final String certificateName;
        private final Integer acquiredYear;

        public static StudentCertificateResult from(StudentCertificate certificate) {
            return new StudentCertificateResult(certificate.getCertificateName(), certificate.getAcquiredYear());
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentReceivedReviewResult {

        private final String storeName;
        private final List<StudentSpecialtyCategoryResult> specialtyCategories;
        private final Integer rating;
        private final String content;
        private final LocalDate createdAt;

        /** 작성일은 서버 로컬 시각 기준 날짜만 내린다. 분류는 리뷰가 달린 의뢰에 연결된 전체 분류다. */
        public static StudentReceivedReviewResult of(
                Review review, String storeName, List<StudentSpecialtyCategoryResult> specialtyCategories) {
            return StudentReceivedReviewResult.builder()
                    .storeName(storeName)
                    .specialtyCategories(List.copyOf(specialtyCategories))
                    .rating(review.getRating())
                    .content(review.getContent())
                    .createdAt(review.getCreatedAt() == null ? null : review.getCreatedAt().toLocalDate())
                    .build();
        }
    }
}
