package com.gakkum.backend.domain.specialty.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyCategoryResponse;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyResponse;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

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

    @Transactional(readOnly = true)
    public Map<Long, SpecialtyDetail> getSpecialtyDetails(Collection<Long> specialtyIds) {

        // 비어있다면
        if (specialtyIds.isEmpty()) {
            return Map.of();
        }

        List<Specialty> specialties = specialtyRepository.findAllById(specialtyIds);
        List<Long> categoryIds = specialties.stream()
                .map(Specialty::getSpecialtyCategoryId)
                .distinct()
                .toList();
        Map<Long, SpecialtyCategory> categoriesById = specialtyCategoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(SpecialtyCategory::getId, category -> category));

        return specialties.stream()
                .map(specialty -> {
                    SpecialtyCategory category = categoriesById.get(specialty.getSpecialtyCategoryId());
                    if (category == null) {
                        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                    }
                    return SpecialtyDetail.of(
                            specialty.getId(), specialty.getName(), category.getId(), category.getName());
                })
                .collect(Collectors.toMap(SpecialtyDetail::getId, detail -> detail));
    }
}
