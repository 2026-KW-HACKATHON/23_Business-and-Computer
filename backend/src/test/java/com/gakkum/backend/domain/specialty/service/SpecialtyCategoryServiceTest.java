package com.gakkum.backend.domain.specialty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyCategoryResponse;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@DisplayName("SpecialtyCategoryService - 대분류/특기 목록 조회 서비스")
class SpecialtyCategoryServiceTest {

    private final SpecialtyCategoryRepository specialtyCategoryRepository = mock(SpecialtyCategoryRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final SpecialtyCategoryService specialtyCategoryService =
            new SpecialtyCategoryService(specialtyCategoryRepository, specialtyRepository);

    @Test
    @DisplayName("특기를 소속 대분류별로 묶고 대분류/특기 모두 ID 오름차순을 유지한다")
    void groupsSpecialtiesByCategoryPreservingIdOrder() {
        // 대분류 2개(IT/개발, 디자인)와 각 대분류에 속한 특기들을 준비
        when(specialtyCategoryRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                category(1L, "IT/개발"),
                category(2L, "디자인")));
        when(specialtyRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                specialty(1L, 1L, "백엔드"),
                specialty(2L, 1L, "프론트엔드"),
                specialty(3L, 2L, "UI/UX")));

        List<SpecialtyCategoryResponse> result = specialtyCategoryService.getSpecialtyCategories();

        logResult(result);

        // 대분류 2개가 순서대로 반환되는지 확인
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("IT/개발");
        // 첫 번째 대분류(IT/개발)에 특기 2개가 ID 순서대로 중첩되는지 확인
        assertThat(result.get(0).getSpecialties()).hasSize(2);
        assertThat(result.get(0).getSpecialties().get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getSpecialties().get(0).getName()).isEqualTo("백엔드");
        assertThat(result.get(0).getSpecialties().get(1).getId()).isEqualTo(2L);
        // 두 번째 대분류(디자인)에는 자신에게 속한 특기만 포함되는지 확인
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getSpecialties()).hasSize(1);
        assertThat(result.get(1).getSpecialties().get(0).getName()).isEqualTo("UI/UX");
    }

    @Test
    @DisplayName("소속된 특기가 없는 대분류는 빈 특기 목록을 반환한다")
    void returnsEmptySpecialtiesForCategoryWithoutAny() {
        // 대분류는 존재하지만 그 대분류에 속한 특기가 하나도 없는 상황을 준비
        when(specialtyCategoryRepository.findAllByOrderByIdAsc()).thenReturn(List.of(category(1L, "IT/개발")));
        when(specialtyRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        List<SpecialtyCategoryResponse> result = specialtyCategoryService.getSpecialtyCategories();

        logResult(result);

        // 대분류는 그대로 반환되지만 specialties는 빈 목록이어야 함
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSpecialties()).isEmpty();
    }

    @Test
    @DisplayName("등록된 대분류가 하나도 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenNoCategoriesExist() {
        // 대분류/특기 데이터가 전혀 없는 초기 상태를 준비
        when(specialtyCategoryRepository.findAllByOrderByIdAsc()).thenReturn(List.of());
        when(specialtyRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        List<SpecialtyCategoryResponse> result = specialtyCategoryService.getSpecialtyCategories();

        logResult(result);

        // 예외 없이 빈 목록이 반환되는지 확인
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("선택된 특기만 일괄 조회하고 대분류 이름을 함께 반환한다")
    void returnsDetailsForSelectedSpecialties() {
        when(specialtyRepository.findAllById(List.of(11L, 21L))).thenReturn(List.of(
                specialty(11L, 1L, "백엔드"), specialty(21L, 2L, "디자인")));
        when(specialtyCategoryRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(
                category(1L, "개발"), category(2L, "디자인")));

        Map<Long, SpecialtyDetail> result = specialtyCategoryService.getSpecialtyDetails(List.of(11L, 21L));

        assertThat(result).hasSize(2);
        assertThat(result.get(11L).getName()).isEqualTo("백엔드");
        assertThat(result.get(11L).getCategoryId()).isEqualTo(1L);
        assertThat(result.get(11L).getCategoryName()).isEqualTo("개발");
        assertThat(result.get(21L).getCategoryName()).isEqualTo("디자인");
    }

    @Test
    @DisplayName("선택된 특기가 없으면 저장소를 조회하지 않는다")
    void returnsEmptyDetailsWithoutQueries() {
        assertThat(specialtyCategoryService.getSpecialtyDetails(List.of())).isEmpty();
        verifyNoInteractions(specialtyRepository, specialtyCategoryRepository);
    }

    // 조회 결과를 사람이 읽기 쉬운 형태로 로그에 출력
    private void logResult(List<SpecialtyCategoryResponse> result) {
        log.info("조회된 대분류 수: {}", result.size());
        result.forEach(category -> log.info(
                "- [{}] {} : {}",
                category.getId(),
                category.getName(),
                category.getSpecialties().stream()
                        .map(specialty -> specialty.getId() + ":" + specialty.getName())
                        .toList()));
    }

    private SpecialtyCategory category(Long id, String name) {
        return SpecialtyCategory.builder()
                .id(id)
                .name(name)
                .build();
    }

    private Specialty specialty(Long id, Long categoryId, String name) {
        return Specialty.builder()
                .id(id)
                .specialtyCategoryId(categoryId)
                .name(name)
                .build();
    }
}
