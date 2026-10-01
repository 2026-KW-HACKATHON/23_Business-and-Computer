package com.gakkum.backend.domain.owner.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gakkum.backend.domain.owner.entity.Owner;

public interface OwnerRepository extends JpaRepository<Owner, Long> {
    boolean existsByBusinessNumber(String businessNumber);

    Optional<Owner> findByUserId(String userId);

    /*
     * 탐색 목록용 매장. 커서 경계는 정렬 키 튜플 (createdAt, id)의 대소 비교라 메서드 이름으로 표현할 수 없다.
     * 첫 페이지는 저장 범위 밖 시각과 Long 최솟값·최댓값을 경계로 넘겨 모든 행을 포함한다.
     */

    @Query("""
            select o from Owner o
            where o.createdAt is not null
              and (o.createdAt, o.id) < (:createdAt, :idBound)
            order by o.createdAt desc, o.id desc
            """)
    List<Owner> findExploreLatest(@Param("createdAt") LocalDateTime createdAt, @Param("idBound") Long idBound,
            Limit limit);

    @Query("""
            select o from Owner o
            where o.createdAt is not null
              and (o.createdAt, o.id) > (:createdAt, :idBound)
            order by o.createdAt asc, o.id asc
            """)
    List<Owner> findExploreOldest(@Param("createdAt") LocalDateTime createdAt, @Param("idBound") Long idBound,
            Limit limit);

    @Query("""
            select o from Owner o
            where o.categoryId = :categoryId
              and o.createdAt is not null
              and (o.createdAt, o.id) < (:createdAt, :idBound)
            order by o.createdAt desc, o.id desc
            """)
    List<Owner> findExploreLatestInCategory(@Param("categoryId") Long categoryId,
            @Param("createdAt") LocalDateTime createdAt, @Param("idBound") Long idBound, Limit limit);

    @Query("""
            select o from Owner o
            where o.categoryId = :categoryId
              and o.createdAt is not null
              and (o.createdAt, o.id) > (:createdAt, :idBound)
            order by o.createdAt asc, o.id asc
            """)
    List<Owner> findExploreOldestInCategory(@Param("categoryId") Long categoryId,
            @Param("createdAt") LocalDateTime createdAt, @Param("idBound") Long idBound, Limit limit);
}
