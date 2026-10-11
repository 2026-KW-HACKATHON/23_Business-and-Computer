package com.gakkum.backend.application.student.dto;

import java.util.List;

import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentMeSummaryResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 내 정보 조회(GET /students/me)와 같은 이름·형식의 필드 중 내 정보 화면에 쓰는 것만 담는다
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentMeSummaryResponse {

    private final Long studentProfileId;
    private final String profileImageUrl;
    private final String name;
    private final String university;
    private final String studentNumber;
    private final String major;
    private final String introduction;
    private final String portfolioUrl;
    private final Long completedJobCount;
    private final List<StudentMeResponse.SpecialtyCategory> specialtyCategories;
    private final List<StudentMeResponse.Certificate> certificates;

    public static StudentMeSummaryResponse from(StudentMeSummaryResult result) {
        return StudentMeSummaryResponse.builder()
                .studentProfileId(result.getStudentProfileId())
                .profileImageUrl(result.getProfileImageUrl())
                .name(result.getName())
                .university(result.getUniversity())
                .studentNumber(result.getStudentNumber())
                .major(result.getMajor())
                .introduction(result.getIntroduction())
                .portfolioUrl(result.getPortfolioUrl())
                .completedJobCount(result.getCompletedJobCount())
                .specialtyCategories(result.getSpecialtyCategories().stream()
                        .map(StudentMeResponse.SpecialtyCategory::from)
                        .toList())
                .certificates(result.getCertificates().stream().map(StudentMeResponse.Certificate::from).toList())
                .build();
    }
}
