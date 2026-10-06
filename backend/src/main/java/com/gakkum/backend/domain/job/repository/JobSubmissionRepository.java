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

    /**
     * 수정 요청 전에 잠금 없이 읽는 제출물의 의뢰·검토 상태·수정 번호.
     * 엔티티로 읽으면 영속성 컨텍스트에 남아 의뢰 잠금 후 조회가 갱신되지 않은 상태를 돌려주므로 프로젝션으로 읽는다.
     */
    Optional<ReviewTargetProjection> findReviewTargetById(Long submissionId);

    interface ReviewTargetProjection {
        Long getJobId();

        JobSubmissionReviewStatus getReviewStatus();

        Integer getRevisionNumber();
    }

    boolean existsByJobId(Long jobId);

    Optional<JobSubmission> findFirstByJobIdOrderByRevisionNumberDesc(Long jobId);

    List<JobSubmission> findByJobIdOrderByRevisionNumberAsc(Long jobId);
}
