package com.gakkum.backend.application.job.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.gakkum.backend.application.job.dto.JobApplicantProfileResponse.StudentInfo;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ApplicantStudentResult;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.user.entity.User;

@DisplayName("학생 프로필 응답의 학생 정보")
class JobApplicantProfileResponseTest {

    @ParameterizedTest
    @CsvSource(value = { "2024402001,24", "2019000001,19", "2024,24", "202,NULL", "NULL,NULL" }, nullValues = "NULL")
    @DisplayName("학번 전체 대신 입학연도 두 자리(셋째·넷째 자리)만 내리고, 학번이 없거나 네 자리보다 짧으면 null로 내린다")
    void exposesOnlyAdmissionYear(String studentNumber, String expected) {
        Student student = Student.builder()
                .id(7L)
                .userId("01K58M6PJV8VAJMXHBHJ2PNB7S")
                .university("광운대학교")
                .major("소프트웨어학부")
                .studentNumber(studentNumber)
                .build();
        User studentUser = User.builder().id("01K58M6PJV8VAJMXHBHJ2PNB7S").name("김가꿈").build();

        StudentInfo info = StudentInfo.from(ApplicantStudentResult.of(student, studentUser));

        assertThat(info.getStudentNumber()).isEqualTo(expected);
        assertThat(info.getStudentProfileId()).isEqualTo(7L);
        assertThat(info.getName()).isEqualTo("김가꿈");
    }
}
