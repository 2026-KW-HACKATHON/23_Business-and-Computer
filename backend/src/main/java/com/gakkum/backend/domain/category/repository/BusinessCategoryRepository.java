package com.gakkum.backend.domain.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.category.entity.BusinessCategory;

public interface BusinessCategoryRepository extends JpaRepository<BusinessCategory, Long> {
    List<BusinessCategory> findAllByOrderByIdAsc();

    Optional<BusinessCategory> findFirstByOrderByIdAsc();
}
