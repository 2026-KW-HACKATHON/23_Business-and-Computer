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
}
