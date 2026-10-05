package com.gakkum.backend.domain.category.dto;

import com.gakkum.backend.domain.category.entity.BusinessCategory;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BusinessCategoryResponse {

    private final Long id;
    private final String name;

    public static BusinessCategoryResponse from(BusinessCategory category) {
        return new BusinessCategoryResponse(category.getId(), category.getName());
    }
}
