package com.gakkum.backend.domain.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class StudentProfileLookupTest {

    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final StudentService studentService = new StudentService(studentRepository);

    @Test
    @DisplayName("선택된 학생 프로필을 일괄 조회한다")
    void returnsStudentsById() {
        when(studentRepository.findAllById(List.of(7L, 8L))).thenReturn(List.of(
                Student.builder().id(8L).studentNumber("2024123456").major("시각디자인학부").build(),
                Student.builder().id(7L).studentNumber("2023123456").major("컴퓨터정보공학부").build()));

        assertThat(studentService.getStudentProfilesByIds(List.of(7L, 8L))).containsOnlyKeys(7L, 8L);
    }

    @Test
    @DisplayName("선택된 학생 ID가 없으면 조회 오류를 발생시킨다")
    void rejectsNullStudentId() {
        assertThatThrownBy(() -> studentService.getStudentProfilesByIds(Collections.singletonList(null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
        verifyNoInteractions(studentRepository);
    }

    @Test
    @DisplayName("선택된 학생 프로필이 저장소에 없으면 조회 오류를 발생시킨다")
    void rejectsMissingStudentProfile() {
        when(studentRepository.findAllById(List.of(7L))).thenReturn(List.of());

        assertThatThrownBy(() -> studentService.getStudentProfilesByIds(List.of(7L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
