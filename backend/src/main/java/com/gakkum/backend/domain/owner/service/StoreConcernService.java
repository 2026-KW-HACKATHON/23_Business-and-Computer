package com.gakkum.backend.domain.owner.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.SaveStoreConcernCommand;
import com.gakkum.backend.domain.owner.entity.StoreConcern;
import com.gakkum.backend.domain.owner.repository.StoreConcernRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 가게 고민. 가게마다 해결되지 않은 고민은 하나이고, 해결한 고민은 resolvedAt과 함께 남는다. */
@Service
@RequiredArgsConstructor
public class StoreConcernService {

    private final StoreConcernRepository storeConcernRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<StoreConcern> findOpenConcern(Long ownerProfileId) {
        return storeConcernRepository.findByOwnerProfileIdAndResolvedAtIsNull(ownerProfileId);
    }

    /**
     * 해결되지 않은 고민이 있으면 통째로 고치고, 없으면 새로 만든다.
     * 두 요청이 동시에 처음 고민을 만들어 부분 유니크 인덱스에 걸리면 409로 거부한다.
     */
    @Transactional
    public StoreConcern saveConcern(Long ownerProfileId, SaveStoreConcernCommand command) {
        Optional<StoreConcern> open = storeConcernRepository.findByOwnerProfileIdAndResolvedAtIsNull(ownerProfileId);
        if (open.isPresent()) {
            StoreConcern concern = open.get();
            concern.update(command.getTitle(), command.getDescription(), command.getSpecialtyCategoryId());
            return storeConcernRepository.saveAndFlush(concern);
        }
        try {
            return storeConcernRepository.saveAndFlush(StoreConcern.create(
                    ownerProfileId, command.getTitle(), command.getDescription(), command.getSpecialtyCategoryId()));
        } catch (DataIntegrityViolationException exception) {
            // 다른 무결성 오류(길이·NOT NULL 등)는 동시 저장이 아니므로 그대로 올린다
            String message = exception.getMessage();
            if (message != null && message.contains(StoreConcern.OPEN_OWNER_UNIQUE_INDEX)) {
                throw new BusinessException(ErrorCode.STORE_CONCERN_CONFLICT);
            }
            throw exception;
        }
    }

    /** 해결되지 않은 고민을 해결로 내린다. 없으면 404로 거부한다. 해결 시각은 다른 시각과 같이 UTC로 저장한다. */
    @Transactional
    public void resolveConcern(Long ownerProfileId) {
        StoreConcern concern = storeConcernRepository.findByOwnerProfileIdAndResolvedAtIsNull(ownerProfileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_CONCERN_NOT_FOUND));
        concern.resolve(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        storeConcernRepository.save(concern);
    }

    /** 사장님 프로필 ID별 해결되지 않은 고민. 고민이 없는 가게는 맵에 없다. */
    @Transactional(readOnly = true)
    public Map<Long, StoreConcern> getOpenConcerns(Collection<Long> ownerProfileIds) {
        if (ownerProfileIds.isEmpty()) {
            return Map.of();
        }
        return storeConcernRepository.findAllByOwnerProfileIdInAndResolvedAtIsNull(ownerProfileIds).stream()
                .collect(Collectors.toMap(StoreConcern::getOwnerProfileId, concern -> concern));
    }
}
