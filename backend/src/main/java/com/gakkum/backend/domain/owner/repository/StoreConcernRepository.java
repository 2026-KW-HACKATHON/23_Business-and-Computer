package com.gakkum.backend.domain.owner.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gakkum.backend.domain.owner.entity.StoreConcern;

public interface StoreConcernRepository extends JpaRepository<StoreConcern, Long> {

    Optional<StoreConcern> findByOwnerProfileIdAndResolvedAtIsNull(Long ownerProfileId);

    List<StoreConcern> findAllByOwnerProfileIdInAndResolvedAtIsNull(Collection<Long> ownerProfileIds);

    /*
     * 학생 홈 「새로 올라온 가게 고민」. 탐색 가게 목록(OwnerRepository.findExplore*)과 같은 가게 범위
     * (같은 데모 세션, 생성 시각이 있는 가게)에서 해결되지 않은 고민을 최근에 올리거나 고친 순으로 읽는다.
     * 같은 시각이면 탐색 가게 목록의 등록순을 따른다.
     */
    @Query("""
            select c from StoreConcern c, Owner o
            where o.id = c.ownerProfileId
              and c.resolvedAt is null
              and o.demoSessionId is not distinct from :demoSessionId
              and o.createdAt is not null
            order by c.updatedAt desc nulls last, o.createdAt asc, o.id asc
            """)
    List<StoreConcern> findNewestOpen(@Param("demoSessionId") String demoSessionId, Limit limit);
}
