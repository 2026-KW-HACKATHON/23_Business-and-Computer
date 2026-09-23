package com.gakkum.backend.domain.specialty.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;

public interface SpecialtyCategoryRepository extends JpaRepository<SpecialtyCategory, Long> {
    List<SpecialtyCategory> findAllByOrderByIdAsc();
}
