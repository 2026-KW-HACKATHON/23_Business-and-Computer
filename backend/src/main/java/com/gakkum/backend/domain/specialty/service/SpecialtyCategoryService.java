package com.gakkum.backend.domain.specialty.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyCategoryResponse;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyResponse;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpecialtyCategoryService {

    private final SpecialtyCategoryRepository specialtyCategoryRepository;
    private final SpecialtyRepository specialtyRepository;

    @Transactional(readOnly = true)
    public List<SpecialtyCategoryResponse> getSpecialtyCategories() {
        List<SpecialtyCategory> categories = specialtyCategoryRepository.findAllByOrderByIdAsc();
        Map<Long, List<Specialty>> specialtiesByCategoryId = specialtyRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(
                        Specialty::getSpecialtyCategoryId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return categories.stream()
                .map(category -> SpecialtyCategoryResponse.of(
                        category.getId(),
                        category.getName(),
                        specialtiesByCategoryId
                                .getOrDefault(category.getId(), List.of())
                                .stream()
                                .map(specialty -> SpecialtyResponse.of(specialty.getId(), specialty.getName()))
                                .toList()))
                .toList();
    }
}
