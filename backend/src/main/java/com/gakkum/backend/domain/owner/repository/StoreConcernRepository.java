package com.gakkum.backend.domain.owner.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.owner.entity.StoreConcern;

public interface StoreConcernRepository extends JpaRepository<StoreConcern, Long> {

    Optional<StoreConcern> findByOwnerProfileIdAndResolvedAtIsNull(Long ownerProfileId);

    List<StoreConcern> findAllByOwnerProfileIdInAndResolvedAtIsNull(Collection<Long> ownerProfileIds);
}
