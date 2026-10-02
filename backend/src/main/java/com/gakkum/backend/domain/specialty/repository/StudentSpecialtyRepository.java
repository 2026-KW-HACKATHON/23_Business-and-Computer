package com.gakkum.backend.domain.specialty.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.specialty.entity.StudentSpecialty;

public interface StudentSpecialtyRepository extends JpaRepository<StudentSpecialty, Long> {
}
