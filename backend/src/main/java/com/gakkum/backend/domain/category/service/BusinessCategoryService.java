package com.gakkum.backend.domain.category.service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public void validateCategoryExists(Long categoryId) {
        if (!businessCategoryRepository.existsById(categoryId)) {
            throw new BusinessException(ErrorCode.BUSINESS_CATEGORY_NOT_FOUND);
        }
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
