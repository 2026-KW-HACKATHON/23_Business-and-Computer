package com.gakkum.backend.domain.category.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.category.dto.BusinessCategoryResponse;
import com.gakkum.backend.domain.category.entity.BusinessCategory;
import com.gakkum.backend.domain.category.repository.BusinessCategoryRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BusinessCategoryService {

    private final BusinessCategoryRepository businessCategoryRepository;

    @Transactional(readOnly = true)
    public List<BusinessCategoryResponse> getBusinessCategories() {
        return businessCategoryRepository.findAllByOrderByIdAsc().stream()
                .map(BusinessCategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public void validateCategoryExists(Long categoryId) {
        if (!businessCategoryRepository.existsById(categoryId)) {
            throw new BusinessException(ErrorCode.BUSINESS_CATEGORY_NOT_FOUND);
        }
    }

    /** 데모 매장에 쓸 업종. 가장 작은 ID의 업종을 고르고, 업종 기준 데이터가 없으면 서버 설정 오류(500)다. */
    @Transactional(readOnly = true)
    public Long getFirstCategoryId() {
        return businessCategoryRepository.findFirstByOrderByIdAsc()
                .map(BusinessCategory::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    /**
     * 업종 ID별 이름을 한 번에 조회한다.
     * @return 요청한 업종 중 하나라도 없으면 참조 무결성 오류(500)
     */
    @Transactional(readOnly = true)
    public Map<Long, String> getCategoryNames(Collection<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = businessCategoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(BusinessCategory::getId, BusinessCategory::getName));
        if (!names.keySet().containsAll(categoryIds)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return names;
    }
}
