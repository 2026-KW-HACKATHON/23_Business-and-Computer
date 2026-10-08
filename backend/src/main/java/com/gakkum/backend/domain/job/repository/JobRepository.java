package com.gakkum.backend.domain.job.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;

import jakarta.persistence.LockModeType;

public interface JobRepository extends JpaRepository<Job, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Job> findLockedById(Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Job> findByIdAndOwnerProfileId(Long jobId, Long ownerProfileId);

    /** 사장님 본인 의뢰를 잠그지 않고 읽는다. 읽기 전용 트랜잭션에서는 잠금 조회(SELECT ... FOR UPDATE)를 쓸 수 없다. */
    Optional<Job> findJobByIdAndOwnerProfileId(Long jobId, Long ownerProfileId);

    /** 제안으로 만든 의뢰. 같은 제안의 결제 승인을 순서대로 처리하도록 의뢰 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Job> findLockedByProposalId(Long proposalId);

    Optional<Job> findByProposalId(Long proposalId);

    /**
     * 작업 시작 전에 잠금 없이 읽는 의뢰의 제안·담당 학생.
     * 엔티티로 읽으면 영속성 컨텍스트에 남아 잠금 후 조회가 갱신되지 않은 상태를 돌려주므로 프로젝션으로 읽는다.
     */
    Optional<StartTargetProjection> findProjectedById(Long jobId);

    interface StartTargetProjection {
        Long getProposalId();

        Long getSelectedStudentProfileId();
    }

    /** 의뢰서 거절 전에 잠금 없이 읽는 의뢰의 격리 범위·제안·담당 학생. 작업 시작과 같은 이유로 프로젝션으로 읽는다. */
    Optional<DeclineTargetProjection> findDeclineTargetById(Long jobId);

    interface DeclineTargetProjection {
        Long getProposalId();

        Long getSelectedStudentProfileId();

        String getDemoSessionId();
    }

    /** 수정 요청 전에 잠금 없이 읽는 본인 의뢰의 상태·수정 가능 횟수. 작업 시작과 같은 이유로 프로젝션으로 읽는다. */
    Optional<RevisionRequestTargetProjection> findRevisionRequestTargetByIdAndOwnerProfileId(
            Long jobId, Long ownerProfileId);

    interface RevisionRequestTargetProjection {
        JobStatus getStatus();

        Integer getRevisionCount();
    }

    List<Job> findByProposalIdIn(Collection<Long> proposalIds);

    /** demoSessionId가 조회자와 같은 의뢰만 고른다. 실제 사용자는 null이고 메서드 이름 쿼리는 null을 IS NULL로 비교한다. */
    List<Job> findByIdInAndDemoSessionId(Collection<Long> jobIds, String demoSessionId);

    List<Job> findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(Long ownerProfileId, JobStatus status);
    List<Job> findByOwnerProfileIdAndStatusInOrderByCompletedAtDescIdDesc(
            Long ownerProfileId, Collection<JobStatus> statuses);
    List<Job> findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(
            Long studentProfileId, JobStatus status);

    long countBySelectedStudentProfileIdAndStatus(Long studentProfileId, JobStatus status);

    /** 사장님의 의뢰 중 주어진 상태(취소)를 뺀 나머지의 수. */
    long countByOwnerProfileIdAndStatusNot(Long ownerProfileId, JobStatus excludedStatus);

    long countByOwnerProfileIdAndStatus(Long ownerProfileId, JobStatus status);

    /*
     * 학생별 의뢰 수는 GROUP BY 집계가 필요해 메서드 이름으로 표현할 수 없다.
     * 해당 상태의 의뢰가 없는 학생은 행이 없다.
     */
    @Query("""
            select j.selectedStudentProfileId as studentProfileId, count(j) as jobCount
            from Job j
            where j.selectedStudentProfileId in :studentProfileIds
              and j.status = :status
            group by j.selectedStudentProfileId
            """)
    List<StudentJobCount> countByStudentProfileIdsAndStatus(
            @Param("studentProfileIds") Collection<Long> studentProfileIds, @Param("status") JobStatus status);

    interface StudentJobCount {
        Long getStudentProfileId();

        Long getJobCount();
    }

    /*
     * 자동 완료 대상 의뢰 ID. 의뢰의 최신 제출물(수정 번호가 가장 큰 행)만 골라 검토 상태와 제출 시각을 비교해야 해서
     * 연관관계가 없는 JobSubmission을 EXISTS와 MAX 서브쿼리로 확인하며, 메서드 이름으로 표현할 수 없다.
     * 데모 의뢰(demoSessionId가 있는 행)는 빼고, afterJobId보다 큰 ID를 오름차순으로 읽는다.
     */
    @Query("""
            select j.id from Job j
            where j.id > :afterJobId
              and j.status = :jobStatus
              and j.demoSessionId is null
              and exists (
                    select 1 from JobSubmission s
                    where s.jobId = j.id
                      and s.reviewStatus = :reviewStatus
                      and s.createdAt <= :submittedUntil
                      and s.revisionNumber = (
                            select max(latest.revisionNumber) from JobSubmission latest
                            where latest.jobId = j.id))
            order by j.id asc
            """)
    List<Long> findAutoCompletableJobIds(@Param("afterJobId") Long afterJobId,
            @Param("jobStatus") JobStatus jobStatus,
            @Param("reviewStatus") JobSubmissionReviewStatus reviewStatus,
            @Param("submittedUntil") LocalDateTime submittedUntil, Limit limit);

    List<Job> findByOwnerProfileId(Long ownerProfileId);
    List<Job> findBySelectedStudentProfileId(Long studentProfileId);

    /*
     * 탐색 목록용 의뢰. excludedStatus(취소)와 제안으로 만든 의뢰(proposalId가 있는 행)를 빼고,
     * 커서 경계는 정렬 키 튜플 (createdAt, id)의 대소 비교다. 제안으로 만든 의뢰는 상태와 무관하게 뺀다.
     * 대분류 없는 조회는 경계 뒤를 정렬 순서상 연속된 두 구간(경계 시각과 같은 행 → 경계 시각 이전·이후)으로 나눈
     * 메서드 이름 쿼리로 읽고 서비스가 이어 붙인다. 각 구간은 (created_at, id) 인덱스의 연속 범위다.
     * idBound에 Long 최솟값·최댓값을 넣어 경계 시각과 같은 행 전체를 빼거나 포함한다.
     * demoSessionId가 조회자와 같은 의뢰만 고른다. 실제 사용자는 null이고 메서드 이름 쿼리는 null을 IS NULL로 비교한다.
     */

    List<Job> findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtAndIdLessThanOrderByIdDesc(
            String demoSessionId, JobStatus excludedStatus, LocalDateTime createdAt, Long idBound, Limit limit);

    List<Job> findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            String demoSessionId, JobStatus excludedStatus, LocalDateTime createdAt, Limit limit);

    List<Job> findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtAndIdGreaterThanOrderByIdAsc(
            String demoSessionId, JobStatus excludedStatus, LocalDateTime createdAt, Long idBound, Limit limit);

    List<Job> findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
            String demoSessionId, JobStatus excludedStatus, LocalDateTime createdAt, Limit limit);

    /*
     * 대분류 조건은 연관관계가 없는 JobSpecialty·Specialty를 EXISTS로 확인해야 해서 메서드 이름으로 표현할 수 없다.
     * 그 대분류의 소분류가 하나라도 연결된 의뢰만 고른다.
     */

    @Query("""
            select j from Job j
            where j.demoSessionId is not distinct from :demoSessionId
              and j.status <> :excludedStatus
              and j.proposalId is null
              and j.createdAt is not null
              and (j.createdAt, j.id) < (:createdAt, :idBound)
              and exists (
                    select 1 from JobSpecialty js join Specialty s on s.id = js.specialtyId
                    where js.jobId = j.id and s.specialtyCategoryId = :categoryId)
            order by j.createdAt desc, j.id desc
            """)
    List<Job> findExploreLatestInCategory(@Param("demoSessionId") String demoSessionId,
            @Param("excludedStatus") JobStatus excludedStatus,
            @Param("categoryId") Long categoryId, @Param("createdAt") LocalDateTime createdAt,
            @Param("idBound") Long idBound, Limit limit);

    @Query("""
            select j from Job j
            where j.demoSessionId is not distinct from :demoSessionId
              and j.status <> :excludedStatus
              and j.proposalId is null
              and j.createdAt is not null
              and (j.createdAt, j.id) > (:createdAt, :idBound)
              and exists (
                    select 1 from JobSpecialty js join Specialty s on s.id = js.specialtyId
                    where js.jobId = j.id and s.specialtyCategoryId = :categoryId)
            order by j.createdAt asc, j.id asc
            """)
    List<Job> findExploreOldestInCategory(@Param("demoSessionId") String demoSessionId,
            @Param("excludedStatus") JobStatus excludedStatus,
            @Param("categoryId") Long categoryId, @Param("createdAt") LocalDateTime createdAt,
            @Param("idBound") Long idBound, Limit limit);
}
