package com.gakkum.backend.domain.job.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;

public interface JobSubmissionRepository extends JpaRepository<JobSubmission, Long> {
    List<JobSubmission> findByJobIdIn(Collection<Long> jobIds);

    List<JobSubmission> findByJobIdInAndReviewStatus(
            Collection<Long> jobIds, JobSubmissionReviewStatus reviewStatus);

    Optional<JobSubmission> findByJobIdAndReviewStatus(Long jobId, JobSubmissionReviewStatus reviewStatus);
}
