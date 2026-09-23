package com.gakkum.backend.domain.specialty.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyCategoryResponse;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyResponse;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.global.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@DisplayName("SpecialtyController - GET /specialties")
class SpecialtyControllerTest {

    private final SpecialtyCategoryService specialtyCategoryService = mock(SpecialtyCategoryService.class);
    private final SpecialtyController controller = new SpecialtyController(specialtyCategoryService);

    @Test
    @DisplayName("대분류 목록을 ApiResponse.success 형식으로 감싸서 중첩 데이터로 반환한다")
    void returnsCategoriesNestedUnderSuccessResponse() throws Exception {
        // 대분류 1개와 그 안에 특기 2개가 있는 서비스 결과를 준비
        SpecialtyCategoryResponse category = SpecialtyCategoryResponse.of(
                1L,
                "IT/개발",
                List.of(SpecialtyResponse.of(1L, "백엔드"), SpecialtyResponse.of(2L, "프론트엔드")));
        when(specialtyCategoryService.getSpecialtyCategories()).thenReturn(List.of(category));

        ApiResponse<List<SpecialtyCategoryResponse>> response = controller.getSpecialties();

        // success=true와 함께 대분류 데이터가 그대로 담기는지 확인
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);

        // 실제 JSON으로 직렬화했을 때도 success 필드와 중첩된 대분류/특기 정보가 포함되는지 확인
        String json = new ObjectMapper().writeValueAsString(response);
        log.info("GET /specialties 응답 JSON: {}", json);
        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"id\":1", "\"name\":\"IT/개발\"", "\"name\":\"백엔드\"", "\"name\":\"프론트엔드\"");
    }

    @Test
    @DisplayName("등록된 대분류가 없으면 빈 목록을 success 응답으로 반환한다")
    void returnsEmptyListWhenNoCategoriesExist() {
        // 서비스가 빈 목록을 반환하는 상황을 준비
        when(specialtyCategoryService.getSpecialtyCategories()).thenReturn(List.of());

        ApiResponse<List<SpecialtyCategoryResponse>> response = controller.getSpecialties();

        log.info("GET /specialties 응답 (대분류 없음): success={}, data={}", response.isSuccess(), response.getData());

        // 실패가 아니라 성공 응답 + 빈 데이터로 반환되는지 확인
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEmpty();
    }
}
