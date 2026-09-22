package com.gakkum.backend.domain.student.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.student.dto.StudentCommandDto.CreateStudentProfileCommand;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    @Transactional
    public Student createStudentProfile(CreateStudentProfileCommand command) {
        Student student = Student.create(
                command.getUserId(),
                command.getUniversity(),
                command.getStudentNumber(),
                command.getMajor(),
                command.getPortfolioUrl(),
                command.getIntroduction(),
                command.getProfileImageUrl());

        return studentRepository.save(student);
    }
}
