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

import jakarta.persistence.LockModeType;

public interface JobRepository extends JpaRepository<Job, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Job> findLockedById(Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Job> findByIdAndOwnerProfileId(Long jobId, Long ownerProfileId);

    List<Job> findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(Long ownerProfileId, JobStatus status);
    List<Job> findByOwnerProfileIdAndStatusInOrderByCompletedAtDescIdDesc(
            Long ownerProfileId, Collection<JobStatus> statuses);
    List<Job> findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(
            Long studentProfileId, JobStatus status);

    List<Job> findByOwnerProfileId(Long ownerProfileId);
    List<Job> findBySelectedStudentProfileId(Long studentProfileId);

    /*
     * 탐색 목록용 의뢰. excludedStatus(취소)를 빼고, 커서 경계는 정렬 키 튜플 (createdAt, id)의 대소 비교다.
     * 대분류 없는 조회는 경계 뒤를 정렬 순서상 연속된 두 구간(경계 시각과 같은 행 → 경계 시각 이전·이후)으로 나눈
     * 메서드 이름 쿼리로 읽고 서비스가 이어 붙인다. 각 구간은 (created_at, id) 인덱스의 연속 범위다.
     * idBound에 Long 최솟값·최댓값을 넣어 경계 시각과 같은 행 전체를 빼거나 포함한다.
     */

    List<Job> findByStatusNotAndCreatedAtAndIdLessThanOrderByIdDesc(
            JobStatus excludedStatus, LocalDateTime createdAt, Long idBound, Limit limit);

    List<Job> findByStatusNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            JobStatus excludedStatus, LocalDateTime createdAt, Limit limit);

    List<Job> findByStatusNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(
            JobStatus excludedStatus, LocalDateTime createdAt, Long idBound, Limit limit);

    List<Job> findByStatusNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
            JobStatus excludedStatus, LocalDateTime createdAt, Limit limit);

    /*
     * 대분류 조건은 연관관계가 없는 JobSpecialty·Specialty를 EXISTS로 확인해야 해서 메서드 이름으로 표현할 수 없다.
     * 그 대분류의 소분류가 하나라도 연결된 의뢰만 고른다.
     */

    @Query("""
            select j from Job j
            where j.status <> :excludedStatus
              and j.createdAt is not null
              and (j.createdAt, j.id) < (:createdAt, :idBound)
              and exists (
                    select 1 from JobSpecialty js join Specialty s on s.id = js.specialtyId
                    where js.jobId = j.id and s.specialtyCategoryId = :categoryId)
            order by j.createdAt desc, j.id desc
            """)
    List<Job> findExploreLatestInCategory(@Param("excludedStatus") JobStatus excludedStatus,
            @Param("categoryId") Long categoryId, @Param("createdAt") LocalDateTime createdAt,
            @Param("idBound") Long idBound, Limit limit);

    @Query("""
            select j from Job j
            where j.status <> :excludedStatus
              and j.createdAt is not null
              and (j.createdAt, j.id) > (:createdAt, :idBound)
              and exists (
                    select 1 from JobSpecialty js join Specialty s on s.id = js.specialtyId
                    where js.jobId = j.id and s.specialtyCategoryId = :categoryId)
            order by j.createdAt asc, j.id asc
            """)
    List<Job> findExploreOldestInCategory(@Param("excludedStatus") JobStatus excludedStatus,
            @Param("categoryId") Long categoryId, @Param("createdAt") LocalDateTime createdAt,
            @Param("idBound") Long idBound, Limit limit);
}
