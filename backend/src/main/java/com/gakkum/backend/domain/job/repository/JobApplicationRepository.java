package com.gakkum.backend.domain.job.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByJobIdInAndStatus(Collection<Long> jobIds, JobApplicationStatus status);

    List<JobApplication> findByStudentProfileId(Long studentProfileId);

    List<JobApplication> findByStudentProfileIdAndJobIdIn(Long studentProfileId, Collection<Long> jobIds);

    List<JobApplication> findByStudentProfileIdAndStatusInOrderByCreatedAtDescIdDesc(
            Long studentProfileId, Collection<JobApplicationStatus> statuses);

    boolean existsByJobIdAndStudentProfileId(Long jobId, Long studentProfileId);
}
