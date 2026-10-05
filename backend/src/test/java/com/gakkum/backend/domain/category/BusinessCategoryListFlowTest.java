package com.gakkum.backend.domain.category;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.category.controller.BusinessCategoryController;
import com.gakkum.backend.domain.category.entity.BusinessCategory;
import com.gakkum.backend.domain.category.repository.BusinessCategoryRepository;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("업종 목록 조회 흐름 - GET /business-categories")
class BusinessCategoryListFlowTest {

    private final BusinessCategoryRepository repository = mock(BusinessCategoryRepository.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BusinessCategoryController controller = new BusinessCategoryController(new BusinessCategoryService(repository));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("ID 오름차순 조회 결과를 실제 ID와 이름만 포함하는 배열로 반환한다")
    void returnsOrderedCategoriesWithOnlyIdAndName() throws Exception {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(
                BusinessCategory.builder().id(5L).name("음식점")
                        .createdAt(timestamp).updatedAt(timestamp).build(),
                BusinessCategory.builder().id(7L).name("카페")
                        .createdAt(timestamp).updatedAt(timestamp).build()));

        mockMvc.perform(get("/business-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].name").value("음식점"))
                .andExpect(jsonPath("$.data[0].*", hasSize(2)))
                .andExpect(jsonPath("$.data[1].id").value(7))
                .andExpect(jsonPath("$.data[1].name").value("카페"))
                .andExpect(jsonPath("$.data[1].*", hasSize(2)));

        verify(repository).findAllByOrderByIdAsc();
    }

    @Test
    @DisplayName("등록된 업종이 없으면 성공 응답의 data에 빈 배열을 반환한다")
    void returnsEmptyArrayWhenNoCategoriesExist() throws Exception {
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of());

        mockMvc.perform(get("/business-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
