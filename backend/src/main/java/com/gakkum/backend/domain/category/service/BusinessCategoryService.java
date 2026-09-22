package com.gakkum.backend.domain.category.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
