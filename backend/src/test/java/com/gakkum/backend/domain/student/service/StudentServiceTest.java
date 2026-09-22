package com.gakkum.backend.domain.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;

class StudentServiceTest {

    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final StudentService studentService = new StudentService(studentRepository);

    @Test
    void createsStudentProfile() {
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Student savedStudent = studentService.createStudentProfile(
                "01K58M6PJV8VAJMXHBHJ2PNB5C",
                "광운대학교",
                "2024402001",
                "컴퓨터정보공학부",
                "https://portfolio.example.com",
                "나의 한 줄 소개",
                "https://image.example.com/profile.png");

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(studentCaptor.capture());
        assertThat(savedStudent).isSameAs(studentCaptor.getValue());
        assertThat(savedStudent.getUserId()).isEqualTo("01K58M6PJV8VAJMXHBHJ2PNB5C");
        assertThat(savedStudent.getUniversity()).isEqualTo("광운대학교");
        assertThat(savedStudent.getStudentNumber()).isEqualTo("2024402001");
        assertThat(savedStudent.getMajor()).isEqualTo("컴퓨터정보공학부");
        assertThat(savedStudent.getPortfolioUrl()).isEqualTo("https://portfolio.example.com");
        assertThat(savedStudent.getIntroduction()).isEqualTo("나의 한 줄 소개");
        assertThat(savedStudent.getProfileImageUrl()).isEqualTo("https://image.example.com/profile.png");
    }
}
