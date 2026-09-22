package com.gakkum.backend.domain.student.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    @Transactional
    public Student createStudentProfile(
            String userId,
            String university,
            String studentNumber,
            String major,
            String portfolioUrl,
            String introduction,
            String profileImageUrl) {
        Student student = Student.create(
                userId,
                university,
                studentNumber,
                major,
                portfolioUrl,
                introduction,
                profileImageUrl);

        return studentRepository.save(student);
    }
}
