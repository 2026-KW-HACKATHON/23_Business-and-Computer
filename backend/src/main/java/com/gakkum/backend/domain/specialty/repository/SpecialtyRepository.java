package com.gakkum.backend.domain.specialty.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.specialty.entity.Specialty;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {
    long countByIdIn(Collection<Long> ids);

    List<Specialty> findAllByOrderByIdAsc();
}
