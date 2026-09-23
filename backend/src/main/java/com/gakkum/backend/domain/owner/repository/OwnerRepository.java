package com.gakkum.backend.domain.owner.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.owner.entity.Owner;

public interface OwnerRepository extends JpaRepository<Owner, Long> {
    boolean existsByBusinessNumber(String businessNumber);

    Optional<Owner> findByUserId(String userId);
}
