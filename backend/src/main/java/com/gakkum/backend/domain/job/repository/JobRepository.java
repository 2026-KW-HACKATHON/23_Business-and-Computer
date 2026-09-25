package com.gakkum.backend.domain.job.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(Long ownerProfileId, JobStatus status);
}
