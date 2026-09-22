package com.gakkum.backend.domain.category.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.category.entity.BusinessCategory;

public interface BusinessCategoryRepository extends JpaRepository<BusinessCategory, Long> {
}
