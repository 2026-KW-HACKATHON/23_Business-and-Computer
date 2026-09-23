package com.gakkum.backend.domain.job.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.job.entity.Job;

public interface JobRepository extends JpaRepository<Job, Long> {
}
