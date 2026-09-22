package com.gakkum.backend.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.category.repository.BusinessCategoryRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class BusinessCategoryServiceTest {

    private final BusinessCategoryRepository businessCategoryRepository = mock(BusinessCategoryRepository.class);
    private final BusinessCategoryService businessCategoryService = new BusinessCategoryService(businessCategoryRepository);

    @Test
    void acceptsExistingCategory() {
        when(businessCategoryRepository.existsById(2L)).thenReturn(true);

        assertThatCode(() -> businessCategoryService.validateCategoryExists(2L)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownCategory() {
        when(businessCategoryRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> businessCategoryService.validateCategoryExists(99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_CATEGORY_NOT_FOUND));
    }
}
