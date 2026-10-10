package com.gakkum.backend.application.owner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.owner.facade.OwnerFacade;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.SaveStoreConcernCommand;
import com.gakkum.backend.domain.owner.dto.OwnerQueryDto.StoreConcernResult;
import com.gakkum.backend.domain.owner.entity.StoreConcern;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("사장님 컨트롤러 - 가게 고민 (/owners/me/concern)")
class OwnerConcernControllerTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 10, 3, 0);

    private final OwnerFacade ownerFacade = mock(OwnerFacade.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OwnerController(ownerFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("해결되지 않은 고민이 없으면 data 없이 성공으로 응답한다")
    void returnsNoDataWithoutConcern() throws Exception {
        when(ownerFacade.getConcern(USERNAME)).thenReturn(Optional.empty());

        mockMvc.perform(get("/owners/me/concern").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("고민은 한 줄·설명·분야·한국 시각으로 내린다")
    void returnsConcern() throws Exception {
        when(ownerFacade.getConcern(USERNAME)).thenReturn(Optional.of(result(5L, "디자인")));

        mockMvc.perform(get("/owners/me/concern").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.concernId").value(7))
                .andExpect(jsonPath("$.data.title").value("평일 점심 손님이 적어요"))
                .andExpect(jsonPath("$.data.description").value("직장인 손님을 늘리고 싶어요"))
                .andExpect(jsonPath("$.data.specialtyCategory.id").value(5))
                .andExpect(jsonPath("$.data.specialtyCategory.name").value("디자인"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-10T12:00:00+09:00"));
    }

    @Test
    @DisplayName("저장은 한 줄을 다듬고 빈 설명을 지운 명령으로 넘기고, 분야를 고르지 않은 고민은 specialtyCategory를 null로 내린다")
    void savesConcern() throws Exception {
        when(ownerFacade.saveConcern(any())).thenReturn(result(null, null));

        mockMvc.perform(put("/owners/me/concern").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  평일 점심 손님이 적어요 \",\"description\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("평일 점심 손님이 적어요"))
                .andExpect(jsonPath("$.data.specialtyCategory").value((Object) null));

        ArgumentCaptor<SaveStoreConcernCommand> captor = ArgumentCaptor.forClass(SaveStoreConcernCommand.class);
        verify(ownerFacade).saveConcern(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo(USERNAME);
        assertThat(captor.getValue().getTitle()).isEqualTo("평일 점심 손님이 적어요");
        assertThat(captor.getValue().getDescription()).isNull();
        assertThat(captor.getValue().getSpecialtyCategoryId()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{}",
            "{\"title\":\"   \"}",
            "{\"title\":\"고민\",\"specialtyCategoryId\":0}",
            "{\"title\":\"고민\",\"specialtyCategoryId\":-1}"})
    @DisplayName("한 줄이 없거나 공백이고, 분야 ID가 양수가 아니면 COMMON_400으로 거부하고 저장하지 않는다")
    void rejectsInvalidBody(String body) throws Exception {
        mockMvc.perform(put("/owners/me/concern").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(ownerFacade);
    }

    @Test
    @DisplayName("한 줄은 60자, 설명은 500자까지 받고 넘으면 COMMON_400으로 거부한다")
    void limitsLengths() throws Exception {
        when(ownerFacade.saveConcern(any())).thenReturn(result(null, null));
        String ok = "{\"title\":\"" + "가".repeat(60) + "\",\"description\":\"" + "나".repeat(500) + "\"}";
        String longTitle = "{\"title\":\"" + "가".repeat(61) + "\"}";
        String longDescription = "{\"title\":\"고민\",\"description\":\"" + "나".repeat(501) + "\"}";

        mockMvc.perform(put("/owners/me/concern").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isOk());
        for (String body : new String[] {longTitle, longDescription}) {
            mockMvc.perform(put("/owners/me/concern").principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
    }

    @Test
    @DisplayName("해결됐어요는 성공으로 응답하고, 해결할 고민이 없으면 STORE_CONCERN_404로 응답한다")
    void resolvesConcern() throws Exception {
        mockMvc.perform(delete("/owners/me/concern").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(ownerFacade).resolveConcern(USERNAME);

        doThrow(new BusinessException(ErrorCode.STORE_CONCERN_NOT_FOUND)).when(ownerFacade).resolveConcern(USERNAME);
        mockMvc.perform(delete("/owners/me/concern").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STORE_CONCERN_404"));
    }

    @Test
    @DisplayName("사장님이 아니면 OWNER_403_CONCERN으로 응답한다")
    void returnsForbiddenForNonOwner() throws Exception {
        when(ownerFacade.getConcern(USERNAME)).thenThrow(new BusinessException(ErrorCode.OWNER_CONCERN_REQUIRED));

        mockMvc.perform(get("/owners/me/concern").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OWNER_403_CONCERN"));
    }

    private StoreConcernResult result(Long categoryId, String categoryName) {
        StoreConcern concern = StoreConcern.builder().id(7L).ownerProfileId(42L).title("평일 점심 손님이 적어요")
                .description(categoryId == null ? null : "직장인 손님을 늘리고 싶어요").specialtyCategoryId(categoryId)
                .createdAt(CREATED_AT).updatedAt(CREATED_AT).build();
        return StoreConcernResult.of(concern, categoryName);
    }
}
