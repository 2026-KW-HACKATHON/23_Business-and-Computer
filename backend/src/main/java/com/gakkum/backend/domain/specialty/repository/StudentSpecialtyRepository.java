package com.gakkum.backend.domain.specialty.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.specialty.entity.StudentSpecialty;

public interface StudentSpecialtyRepository extends JpaRepository<StudentSpecialty, Long> {
    List<StudentSpecialty> findByStudentProfileIdIn(Collection<Long> studentProfileIds);
}
