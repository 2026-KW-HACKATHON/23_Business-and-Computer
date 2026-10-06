package com.gakkum.backend.domain.owner.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.GetExploreStoresCommand;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.UpdateOwnerMeCommand;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final OwnerRepository ownerRepository;

    /** demoSessionId는 데모 로그인이 만든 매장의 격리 범위다. 실제 가입은 null이다. */
    @Transactional
    public Owner createOwnerProfile(CreateOwnerProfileCommand command, String demoSessionId) {
        Owner owner = Owner.create(
                command.getUserId(),
                command.getBusinessNumber(),
                command.getOpenedAt(),
                command.getRepresentativeName(),
                command.getStoreName(),
                command.getCategoryId(),
                command.getStoreAddress(),
                command.getDescription(),
                command.getProfileImageUrl(),
                command.getStoreImageUrls(),
                demoSessionId);

        return ownerRepository.save(owner);
    }

    @Transactional
    public void updateOwnerProfile(Owner owner, UpdateOwnerMeCommand command) {
        owner.updateProfile(
                command.getStoreName(),
                command.getCategoryId(),
                command.getProfileImageUrl(),
                command.getStoreAddress(),
                command.getDescription());
        ownerRepository.save(owner);
    }

    @Transactional(readOnly = true)
    public void validateBusinessNumberAvailable(String businessNumber) {
        if (ownerRepository.existsByBusinessNumber(businessNumber)) {
            throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
        }
    }

    /**
     * 사장님 프로필 ID로 현재 프로필 조회
     * @param ownerProfileId
     * @return 사장님 프로필, 없으면 참조 무결성 오류(500)
     */
    @Transactional(readOnly = true)
    public Owner getOwnerProfileById(Long ownerProfileId) {
        if (ownerProfileId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return ownerRepository.findById(ownerProfileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    /** 요청에서 지정한 사장님 프로필이 요청자와 같은 격리 범위(demoSessionId)에 존재하는지 확인한다. 범위가 다르면 없는 사장님과 같다. */
    @Transactional(readOnly = true)
    public void validateOwnerProfileExists(Long ownerProfileId, String demoSessionId) {
        ownerRepository.findById(ownerProfileId)
                .filter(owner -> Objects.equals(owner.getDemoSessionId(), demoSessionId))
                .orElseThrow(() -> new BusinessException(ErrorCode.OWNER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Owner getOwnerProfile(String userId) {
        return ownerRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OWNER_PROFILE_NOT_FOUND));
    }

    /**
     * 사용자 ID로 사장님 프로필 조회
     * @param userId
     * @return 사장님 프로필, 사장님 프로필이 없으면 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<Owner> findOwnerProfileByUserId(String userId) {
        return ownerRepository.findByUserId(userId);
    }

    /**
     * 사장님 프로필 ID별 매장 이름을 한 번에 조회한다.
     * @return 요청한 프로필 중 하나라도 없으면 참조 무결성 오류(500)
     */
    @Transactional(readOnly = true)
    public Map<Long, String> getStoreNames(Collection<Long> ownerProfileIds) {
        if (ownerProfileIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> storeNames = ownerRepository.findAllById(ownerProfileIds).stream()
                .collect(Collectors.toMap(Owner::getId, Owner::getStoreName));
        if (!storeNames.keySet().containsAll(ownerProfileIds)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return storeNames;
    }

    /**
     * 사장님 프로필 ID별 현재 프로필을 한 번에 조회한다.
     * @return 요청한 프로필 중 하나라도 없으면 참조 무결성 오류(500)
     */
    @Transactional(readOnly = true)
    public Map<Long, Owner> getOwnerProfilesByIds(Collection<Long> ownerProfileIds) {
        if (ownerProfileIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Owner> owners = ownerRepository.findAllById(ownerProfileIds).stream()
                .collect(Collectors.toMap(Owner::getId, owner -> owner));
        if (!owners.keySet().containsAll(ownerProfileIds)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return owners;
    }

    /** 탐색 목록용으로 매장(사장님 프로필)을 커서 경계 뒤부터 정렬 순서대로 limit개까지 읽는다. */
    @Transactional(readOnly = true)
    public List<Owner> getExploreStores(GetExploreStoresCommand command) {
        Limit limit = Limit.of(command.getLimit());
        String demoSessionId = command.getDemoSessionId();
        Long categoryId = command.getBusinessCategoryId();
        if (categoryId == null) {
            return command.isOldestFirst()
                    ? ownerRepository.findExploreOldest(
                            demoSessionId, command.getCreatedAtBound(), command.getIdBound(), limit)
                    : ownerRepository.findExploreLatest(
                            demoSessionId, command.getCreatedAtBound(), command.getIdBound(), limit);
        }
        return command.isOldestFirst()
                ? ownerRepository.findExploreOldestInCategory(
                        demoSessionId, categoryId, command.getCreatedAtBound(), command.getIdBound(), limit)
                : ownerRepository.findExploreLatestInCategory(
                        demoSessionId, categoryId, command.getCreatedAtBound(), command.getIdBound(), limit);
    }
}
