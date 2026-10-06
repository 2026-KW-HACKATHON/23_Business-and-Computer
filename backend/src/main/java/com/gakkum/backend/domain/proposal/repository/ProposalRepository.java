package com.gakkum.backend.domain.proposal.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;

import jakarta.persistence.LockModeType;

/**
 * 탐색 조회의 커서 경계는 정렬 키 튜플 (createdAt, id) 또는 (likeCount, createdAt, id)의 대소 비교다.
 * 대분류 없는 조회는 그 경계 뒤를 정렬 순서상 연속된 구간 여러 개로 나눈 메서드 이름 쿼리로 읽고,
 * 서비스가 앞 구간부터 이어 붙인다. 각 구간은 정렬 인덱스의 연속 범위라 경계부터 인덱스를 따라 읽는다.
 * idBound에 Long 최솟값·최댓값을 넣어 경계 시각과 같은 행 전체를 빼거나 포함한다.
 */
public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    /** 결제 승인·작업 시작·공감 변경·취소가 같은 제안을 순서대로 처리하도록 제안 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Proposal> findLockedById(Long proposalId);

    /** 학생이 보낸 제안 중 주어진 상태(취소)를 뺀 나머지를 최신순으로 읽는다. */
    List<Proposal> findByStudentProfileIdAndStatusNotOrderByCreatedAtDescIdDesc(
            Long studentProfileId, ProposalStatus excludedStatus);

    /** 학생이 보낸 모든 상태의 제안 수. 취소한 제안도 센다. */
    long countByStudentProfileId(Long studentProfileId);

    /** 사장님이 받은 제안 중 주어진 상태(취소)를 뺀 나머지를 최신순으로 읽는다. */
    List<Proposal> findByOwnerProfileIdAndStatusNotOrderByCreatedAtDescIdDesc(
            Long ownerProfileId, ProposalStatus excludedStatus);

    // 탐색 목록은 demoSessionId가 조회자와 같은 제안만 고른다. 실제 사용자는 null이고 메서드 이름 쿼리는 null을 IS NULL로 비교한다
    // 취소된 제안은 페이지 크기와 커서가 어긋나지 않도록 모든 구간에서 조회 조건(excludedStatus)으로 뺀다

    // 최신순: 경계 시각과 같은 행 중 경계 ID 앞 → 경계 시각 이전
    List<Proposal> findByDemoSessionIdAndStatusNotAndCreatedAtAndIdLessThanOrderByIdDesc(
            String demoSessionId, ProposalStatus excludedStatus, LocalDateTime createdAt, Long idBound, Limit limit);

    List<Proposal> findByDemoSessionIdAndStatusNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            String demoSessionId, ProposalStatus excludedStatus, LocalDateTime createdAt, Limit limit);

    // 오래된순: 경계 시각과 같은 행 중 경계 ID 뒤 → 경계 시각 이후
    List<Proposal> findByDemoSessionIdAndStatusNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(
            String demoSessionId, ProposalStatus excludedStatus, LocalDateTime createdAt, Long idBound, Limit limit);

    List<Proposal> findByDemoSessionIdAndStatusNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
            String demoSessionId, ProposalStatus excludedStatus, LocalDateTime createdAt, Limit limit);

    // 좋아요순: 같은 좋아요·같은 시각 중 경계 ID 앞 → 같은 좋아요 중 경계 시각 이전 → 좋아요가 더 적은 제안
    List<Proposal> findByDemoSessionIdAndStatusNotAndLikeCountAndCreatedAtAndIdLessThanOrderByIdDesc(
            String demoSessionId, ProposalStatus excludedStatus, Integer likeCount, LocalDateTime createdAt, Long idBound, Limit limit);

    List<Proposal> findByDemoSessionIdAndStatusNotAndLikeCountAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            String demoSessionId, ProposalStatus excludedStatus, Integer likeCount, LocalDateTime createdAt, Limit limit);

    List<Proposal> findByDemoSessionIdAndStatusNotAndLikeCountLessThanOrderByLikeCountDescCreatedAtDescIdDesc(
            String demoSessionId, ProposalStatus excludedStatus, Integer likeCount, Limit limit);

    /*
     * 대분류 조건은 연관관계가 없는 ProposalSpecialty·Specialty를 EXISTS로 확인해야 해서 메서드 이름으로 표현할 수 없다.
     * 그 대분류의 소분류가 하나라도 연결된 제안만 고르고, 메서드 이름 쿼리와 같이 취소된 제안을 뺀다.
     */

    @Query("""
            select p from Proposal p
            where p.demoSessionId is not distinct from :demoSessionId
              and p.status <> :excludedStatus
              and p.createdAt is not null
              and (p.createdAt, p.id) < (:createdAt, :idBound)
              and exists (
                    select 1 from ProposalSpecialty ps join Specialty s on s.id = ps.specialtyId
                    where ps.proposalId = p.id and s.specialtyCategoryId = :categoryId)
            order by p.createdAt desc, p.id desc
            """)
    List<Proposal> findExploreLatestInCategory(@Param("demoSessionId") String demoSessionId,
            @Param("excludedStatus") ProposalStatus excludedStatus, @Param("categoryId") Long categoryId,
            @Param("createdAt") LocalDateTime createdAt, @Param("idBound") Long idBound, Limit limit);

    @Query("""
            select p from Proposal p
            where p.demoSessionId is not distinct from :demoSessionId
              and p.status <> :excludedStatus
              and p.createdAt is not null
              and (p.createdAt, p.id) > (:createdAt, :idBound)
              and exists (
                    select 1 from ProposalSpecialty ps join Specialty s on s.id = ps.specialtyId
                    where ps.proposalId = p.id and s.specialtyCategoryId = :categoryId)
            order by p.createdAt asc, p.id asc
            """)
    List<Proposal> findExploreOldestInCategory(@Param("demoSessionId") String demoSessionId,
            @Param("excludedStatus") ProposalStatus excludedStatus, @Param("categoryId") Long categoryId,
            @Param("createdAt") LocalDateTime createdAt, @Param("idBound") Long idBound, Limit limit);

    @Query("""
            select p from Proposal p
            where p.demoSessionId is not distinct from :demoSessionId
              and p.status <> :excludedStatus
              and p.createdAt is not null
              and (p.likeCount, p.createdAt, p.id) < (:likeCount, :createdAt, :idBound)
              and exists (
                    select 1 from ProposalSpecialty ps join Specialty s on s.id = ps.specialtyId
                    where ps.proposalId = p.id and s.specialtyCategoryId = :categoryId)
            order by p.likeCount desc, p.createdAt desc, p.id desc
            """)
    List<Proposal> findExploreByLikesInCategory(@Param("demoSessionId") String demoSessionId,
            @Param("excludedStatus") ProposalStatus excludedStatus, @Param("categoryId") Long categoryId,
            @Param("likeCount") Integer likeCount, @Param("createdAt") LocalDateTime createdAt,
            @Param("idBound") Long idBound, Limit limit);
}
