package com.gakkum.backend.domain.student.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.student.entity.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {
    boolean existsByStudentNumber(String studentNumber);
}
