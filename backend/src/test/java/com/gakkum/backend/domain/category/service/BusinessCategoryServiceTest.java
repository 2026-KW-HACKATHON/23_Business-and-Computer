package com.gakkum.backend.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.category.entity.BusinessCategory;
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

    @Test
    @DisplayName("업종 ID별 이름을 한 번에 조회하고 하나라도 없으면 COMMON_500으로 거부한다")
    void getsCategoryNamesAtOnce() {
        when(businessCategoryRepository.findAllById(List.of(2L, 3L))).thenReturn(List.of(
                BusinessCategory.builder().id(2L).name("음식점").build(),
                BusinessCategory.builder().id(3L).name("카페").build()));
        when(businessCategoryRepository.findAllById(List.of(2L, 9L))).thenReturn(List.of(
                BusinessCategory.builder().id(2L).name("음식점").build()));

        assertThat(businessCategoryService.getCategoryNames(List.of(2L, 3L)))
                .isEqualTo(Map.of(2L, "음식점", 3L, "카페"));
        assertThat(businessCategoryService.getCategoryNames(List.of())).isEmpty();
        assertThatThrownBy(() -> businessCategoryService.getCategoryNames(List.of(2L, 9L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
