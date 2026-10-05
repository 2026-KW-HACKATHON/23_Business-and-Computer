package com.gakkum.backend.application.category.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.domain.category.dto.BusinessCategoryResponse;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BusinessCategoryController {

    private final BusinessCategoryService businessCategoryService;

    @GetMapping("/business-categories")
    public ResponseEntity<ApiResponse<List<BusinessCategoryResponse>>> getBusinessCategories() {
        return ResponseEntity.ok(ApiResponse.success(businessCategoryService.getBusinessCategories()));
    }
}
