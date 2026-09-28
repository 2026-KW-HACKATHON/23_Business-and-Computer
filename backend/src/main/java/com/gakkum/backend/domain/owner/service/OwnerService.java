package com.gakkum.backend.domain.owner.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final OwnerRepository ownerRepository;

    @Transactional
    public Owner createOwnerProfile(CreateOwnerProfileCommand command) {
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
                command.getStoreImageUrls());

        return ownerRepository.save(owner);
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

    @Transactional(readOnly = true)
    public Owner getOwnerProfile(String userId) {
        return ownerRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OWNER_PROFILE_NOT_FOUND));
    }
}
