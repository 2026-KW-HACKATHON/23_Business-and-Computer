package com.gakkum.backend.domain.job.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.job.entity.JobSpecialty;

public interface JobSpecialtyRepository extends JpaRepository<JobSpecialty, Long> {
    List<JobSpecialty> findByJobIdIn(Collection<Long> jobIds);
}
