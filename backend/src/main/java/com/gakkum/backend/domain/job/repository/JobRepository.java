package com.gakkum.backend.domain.job.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;

import jakarta.persistence.LockModeType;

public interface JobRepository extends JpaRepository<Job, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Job> findLockedById(Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Job> findByIdAndOwnerProfileId(Long jobId, Long ownerProfileId);

    List<Job> findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(Long ownerProfileId, JobStatus status);
    List<Job> findByOwnerProfileIdAndStatusOrderByCompletedAtDescIdDesc(Long ownerProfileId, JobStatus status);

    List<Job> findByOwnerProfileId(Long ownerProfileId);
    List<Job> findBySelectedStudentProfileId(Long studentProfileId);
}
