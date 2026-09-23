package com.gakkum.backend.domain.specialty.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyListResponse;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SpecialtyController {

    private final SpecialtyCategoryService specialtyCategoryService;

    @GetMapping("/specialties")
    public ApiResponse<SpecialtyListResponse> getSpecialties() {
        return ApiResponse.success(SpecialtyListResponse.from(specialtyCategoryService.getSpecialtyCategories()));
    }
}
