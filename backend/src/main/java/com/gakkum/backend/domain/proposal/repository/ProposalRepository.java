package com.gakkum.backend.domain.proposal.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gakkum.backend.domain.proposal.entity.Proposal;

/**
 * 탐색 조회의 커서 경계는 정렬 키 튜플 (createdAt, id) 또는 (likeCount, createdAt, id)의 대소 비교다.
 * 대분류 없는 조회는 그 경계 뒤를 정렬 순서상 연속된 구간 여러 개로 나눈 메서드 이름 쿼리로 읽고,
 * 서비스가 앞 구간부터 이어 붙인다. 각 구간은 정렬 인덱스의 연속 범위라 경계부터 인덱스를 따라 읽는다.
 * idBound에 Long 최솟값·최댓값을 넣어 경계 시각과 같은 행 전체를 빼거나 포함한다.
 */
public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    /** 학생이 보낸 모든 상태의 제안을 최신순으로 읽는다. */
    List<Proposal> findByStudentProfileIdOrderByCreatedAtDescIdDesc(Long studentProfileId);

    /** 학생이 보낸 모든 상태의 제안 수. */
    long countByStudentProfileId(Long studentProfileId);

    /** 사장님이 받은 모든 상태의 제안을 최신순으로 읽는다. */
    List<Proposal> findByOwnerProfileIdOrderByCreatedAtDescIdDesc(Long ownerProfileId);

    // 최신순: 경계 시각과 같은 행 중 경계 ID 앞 → 경계 시각 이전
    List<Proposal> findByCreatedAtAndIdLessThanOrderByIdDesc(LocalDateTime createdAt, Long idBound, Limit limit);

    List<Proposal> findByCreatedAtLessThanOrderByCreatedAtDescIdDesc(LocalDateTime createdAt, Limit limit);

    // 오래된순: 경계 시각과 같은 행 중 경계 ID 뒤 → 경계 시각 이후
    List<Proposal> findByCreatedAtAndIdGreaterThanOrderByIdAsc(LocalDateTime createdAt, Long idBound, Limit limit);

    List<Proposal> findByCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(LocalDateTime createdAt, Limit limit);

    // 좋아요순: 같은 좋아요·같은 시각 중 경계 ID 앞 → 같은 좋아요 중 경계 시각 이전 → 좋아요가 더 적은 제안
    List<Proposal> findByLikeCountAndCreatedAtAndIdLessThanOrderByIdDesc(
            Integer likeCount, LocalDateTime createdAt, Long idBound, Limit limit);

    List<Proposal> findByLikeCountAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            Integer likeCount, LocalDateTime createdAt, Limit limit);

    List<Proposal> findByLikeCountLessThanOrderByLikeCountDescCreatedAtDescIdDesc(Integer likeCount, Limit limit);

    /*
     * 대분류 조건은 연관관계가 없는 ProposalSpecialty·Specialty를 EXISTS로 확인해야 해서 메서드 이름으로 표현할 수 없다.
     * 그 대분류의 소분류가 하나라도 연결된 제안만 고른다.
     */

    @Query("""
            select p from Proposal p
            where p.createdAt is not null
              and (p.createdAt, p.id) < (:createdAt, :idBound)
              and exists (
                    select 1 from ProposalSpecialty ps join Specialty s on s.id = ps.specialtyId
                    where ps.proposalId = p.id and s.specialtyCategoryId = :categoryId)
            order by p.createdAt desc, p.id desc
            """)
    List<Proposal> findExploreLatestInCategory(@Param("categoryId") Long categoryId,
            @Param("createdAt") LocalDateTime createdAt, @Param("idBound") Long idBound, Limit limit);

    @Query("""
            select p from Proposal p
            where p.createdAt is not null
              and (p.createdAt, p.id) > (:createdAt, :idBound)
              and exists (
                    select 1 from ProposalSpecialty ps join Specialty s on s.id = ps.specialtyId
                    where ps.proposalId = p.id and s.specialtyCategoryId = :categoryId)
            order by p.createdAt asc, p.id asc
            """)
    List<Proposal> findExploreOldestInCategory(@Param("categoryId") Long categoryId,
            @Param("createdAt") LocalDateTime createdAt, @Param("idBound") Long idBound, Limit limit);

    @Query("""
            select p from Proposal p
            where p.createdAt is not null
              and (p.likeCount, p.createdAt, p.id) < (:likeCount, :createdAt, :idBound)
              and exists (
                    select 1 from ProposalSpecialty ps join Specialty s on s.id = ps.specialtyId
                    where ps.proposalId = p.id and s.specialtyCategoryId = :categoryId)
            order by p.likeCount desc, p.createdAt desc, p.id desc
            """)
    List<Proposal> findExploreByLikesInCategory(@Param("categoryId") Long categoryId,
            @Param("likeCount") Integer likeCount, @Param("createdAt") LocalDateTime createdAt,
            @Param("idBound") Long idBound, Limit limit);
}
