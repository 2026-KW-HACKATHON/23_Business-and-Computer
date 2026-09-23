package com.gakkum.backend.domain.job.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.job.entity.JobSpecialty;

public interface JobSpecialtyRepository extends JpaRepository<JobSpecialty, Long> {
}
