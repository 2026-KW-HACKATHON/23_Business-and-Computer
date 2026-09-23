package com.gakkum.backend.domain.specialty;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.domain.specialty.controller.SpecialtyController;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@DisplayName("대분류/특기 목록 조회 전체 플로우 (GET /specialties)")
class SpecialtyListFlowTest {

    private final SpecialtyCategoryRepository specialtyCategoryRepository = mock(SpecialtyCategoryRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 레포지토리만 목(mock)으로 대체하고, 서비스·컨트롤러는 실제 구현을 그대로 사용해 전체 흐름을 검증
        SpecialtyCategoryService specialtyCategoryService =
                new SpecialtyCategoryService(specialtyCategoryRepository, specialtyRepository);
        SpecialtyController controller = new SpecialtyController(specialtyCategoryService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("컨트롤러-서비스-레포지토리 전체 흐름을 거쳐 대분류별로 묶인 특기 목록을 반환한다")
    void returnsSpecialtiesGroupedByCategoryThroughFullFlow() throws Exception {
        // 대분류 2개(IT/개발, 디자인)와 각 대분류에 속한 특기 데이터를 리포지토리 응답으로 준비
        when(specialtyCategoryRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                category(1L, "IT/개발"),
                category(2L, "디자인")));
        when(specialtyRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                specialty(1L, 1L, "백엔드"),
                specialty(2L, 1L, "프론트엔드"),
                specialty(3L, 2L, "UI/UX")));

        // 실제 HTTP 요청으로 컨트롤러 -> 서비스 -> 레포지토리 전체 흐름을 태워서 검증
        MvcResult result = mockMvc.perform(get("/specialties"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("IT/개발"))
                .andExpect(jsonPath("$.data[0].specialties[0].id").value(1))
                .andExpect(jsonPath("$.data[0].specialties[0].name").value("백엔드"))
                .andExpect(jsonPath("$.data[0].specialties[1].name").value("프론트엔드"))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].name").value("디자인"))
                .andExpect(jsonPath("$.data[1].specialties[0].name").value("UI/UX"))
                .andReturn();

        // 응답 JSON을 그대로 로그에 남겨서 눈으로 바로 확인할 수 있게 함
        String responseJson = result.getResponse().getContentAsString();
        log.info("GET /specialties 응답 JSON: {}", responseJson);
    }

    @Test
    @DisplayName("등록된 대분류가 없으면 빈 배열을 success 응답으로 반환한다")
    void returnsEmptyArrayWhenNoCategoriesExist() throws Exception {
        // 대분류/특기가 전혀 등록되지 않은 초기 상태를 준비
        when(specialtyCategoryRepository.findAllByOrderByIdAsc()).thenReturn(List.of());
        when(specialtyRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        MvcResult result = mockMvc.perform(get("/specialties"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        // 데이터가 없는 케이스도 응답 JSON을 로그로 남겨서 실제 형태를 확인할 수 있게 함
        String responseJson = result.getResponse().getContentAsString();
        log.info("GET /specialties 응답 JSON (대분류 없음): {}", responseJson);
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
