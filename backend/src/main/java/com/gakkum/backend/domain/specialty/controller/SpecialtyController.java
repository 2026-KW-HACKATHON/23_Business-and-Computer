package com.gakkum.backend.domain.specialty.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyCategoryResponse;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SpecialtyController {

    private final SpecialtyCategoryService specialtyCategoryService;

    @GetMapping("/specialties")
    public ApiResponse<List<SpecialtyCategoryResponse>> getSpecialties() {
        return ApiResponse.success(specialtyCategoryService.getSpecialtyCategories());
    }
}
