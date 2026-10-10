package com.gakkum.backend.application.explore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.explore.dto.ExploreCommandDto.StoreExploreCommand;
import com.gakkum.backend.application.explore.dto.ExploreCursor;
import com.gakkum.backend.application.explore.dto.ExploreItemType;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.BusinessCategoryResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.StoreExploreResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.StoreItemResult;
import com.gakkum.backend.application.explore.dto.ExploreSort;
import com.gakkum.backend.application.explore.dto.ExploreType;
import com.gakkum.backend.application.explore.dto.StoreExploreCursor;
import com.gakkum.backend.application.explore.dto.StoreExploreSort;
import com.gakkum.backend.application.explore.facade.ExploreFacade;
import com.gakkum.backend.domain.owner.dto.OwnerQueryDto.StoreConcernResult;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.entity.StoreConcern;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("매장 탐색 컨트롤러 (GET /explore/stores)")
class StoreExploreControllerTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 10, 30);

    private final ExploreFacade exploreFacade = mock(ExploreFacade.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExploreController(exploreFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("파라미터가 없으면 최신순·전체 업종·20개·첫 페이지로 조회하고 빈 목록을 그대로 반환한다")
    void usesDefaults() throws Exception {
        when(exploreFacade.exploreStores(any())).thenReturn(StoreExploreResult.of(List.of(), null));

        mockMvc.perform(get("/explore/stores").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.nextCursor").value((Object) null))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        StoreExploreCommand command = captureCommand();
        assertThat(command.getUsername()).isEqualTo(USERNAME);
        assertThat(command.getSort()).isEqualTo(StoreExploreSort.LATEST);
        assertThat(command.getBusinessCategoryId()).isNull();
        assertThat(command.getSize()).isEqualTo(20);
        assertThat(command.getCursor()).isNull();
    }

    @Test
    @DisplayName("같은 조건으로 만든 커서와 정렬·업종·크기를 해석해 파사드에 넘기고 빈 커서는 첫 페이지로 본다")
    void passesConditionsAndCursor() throws Exception {
        when(exploreFacade.exploreStores(any())).thenReturn(StoreExploreResult.of(List.of(), null));
        String cursor = StoreExploreCursor.of(StoreExploreSort.OLDEST, 3L, CREATED_AT, 42L).encode();

        mockMvc.perform(get("/explore/stores").principal(authentication)
                        .param("sort", "OLDEST")
                        .param("businessCategoryId", "3")
                        .param("size", "100")
                        .param("cursor", cursor))
                .andExpect(status().isOk());

        StoreExploreCommand command = captureCommand();
        assertThat(command.getSort()).isEqualTo(StoreExploreSort.OLDEST);
        assertThat(command.getBusinessCategoryId()).isEqualTo(3L);
        assertThat(command.getSize()).isEqualTo(100);
        assertThat(command.getCursor().getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(command.getCursor().getId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("빈 커서는 첫 페이지로 조회한다")
    void treatsBlankCursorAsFirstPage() throws Exception {
        when(exploreFacade.exploreStores(any())).thenReturn(StoreExploreResult.of(List.of(), null));

        mockMvc.perform(get("/explore/stores").principal(authentication).param("cursor", ""))
                .andExpect(status().isOk());

        assertThat(captureCommand().getCursor()).isNull();
    }

    @Test
    @DisplayName("응답은 매장 이름·프로필 사진·업종·주소·프로필 ID·생성 시각·가게 고민을 담고 없는 사진·주소·고민은 null로 내린다")
    void returnsStoreItems() throws Exception {
        Owner full = Owner.builder().id(42L).storeName("가꿈 카페").categoryId(3L)
                .profileImageUrl("https://example.com/owner-profile.jpg").storeAddress("서울특별시 노원구 광운로 20")
                .createdAt(CREATED_AT).build();
        Owner bare = Owner.builder().id(41L).storeName("가꿈 분식").categoryId(2L).createdAt(CREATED_AT).build();
        StoreConcern concern = StoreConcern.builder().id(7L).ownerProfileId(42L).title("평일 점심 손님이 적어요")
                .description("직장인 손님을 늘리고 싶어요").specialtyCategoryId(1L)
                .createdAt(CREATED_AT).updatedAt(CREATED_AT.plusHours(1)).build();
        when(exploreFacade.exploreStores(any())).thenReturn(StoreExploreResult.of(List.of(
                StoreItemResult.of(full, BusinessCategoryResult.of(3L, "카페"), StoreConcernResult.of(concern, "디자인")),
                StoreItemResult.of(bare, BusinessCategoryResult.of(2L, "음식점"), null)), "next"));

        mockMvc.perform(get("/explore/stores").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].storeName").value("가꿈 카페"))
                .andExpect(jsonPath("$.data.items[0].profileImageUrl").value("https://example.com/owner-profile.jpg"))
                .andExpect(jsonPath("$.data.items[0].businessCategory.id").value(3))
                .andExpect(jsonPath("$.data.items[0].businessCategory.name").value("카페"))
                .andExpect(jsonPath("$.data.items[0].storeAddress").value("서울특별시 노원구 광운로 20"))
                .andExpect(jsonPath("$.data.items[0].ownerProfileId").value(42))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-10-01T19:30:00+09:00"))
                .andExpect(jsonPath("$.data.items[0].concern.concernId").value(7))
                .andExpect(jsonPath("$.data.items[0].concern.title").value("평일 점심 손님이 적어요"))
                .andExpect(jsonPath("$.data.items[0].concern.description").value("직장인 손님을 늘리고 싶어요"))
                .andExpect(jsonPath("$.data.items[0].concern.specialtyCategory.id").value(1))
                .andExpect(jsonPath("$.data.items[0].concern.specialtyCategory.name").value("디자인"))
                .andExpect(jsonPath("$.data.items[0].concern.updatedAt").value("2026-10-01T20:30:00+09:00"))
                .andExpect(jsonPath("$.data.items[0].length()").value(7))
                .andExpect(jsonPath("$.data.items[1].storeName").value("가꿈 분식"))
                .andExpect(jsonPath("$.data.items[1].profileImageUrl").value((Object) null))
                .andExpect(jsonPath("$.data.items[1].storeAddress").value((Object) null))
                .andExpect(jsonPath("$.data.items[1].concern").value((Object) null))
                .andExpect(jsonPath("$.data.items[1].length()").value(7))
                .andExpect(jsonPath("$.data.nextCursor").value("next"))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    static Stream<Arguments> invalidRequests() {
        String categoryCursor = StoreExploreCursor.of(StoreExploreSort.LATEST, 3L, CREATED_AT, 1L).encode();
        String exploreCursor = ExploreCursor.of(ExploreSort.LATEST, ExploreType.ALL, null,
                ExploreItemType.JOB, null, CREATED_AT, 1L).encode();
        return Stream.of(
                Arguments.of("없는 정렬", get("/explore/stores").param("sort", "POPULAR")),
                Arguments.of("제안 전용 좋아요순", get("/explore/stores").param("sort", "LIKES")),
                Arguments.of("업종 ID 0", get("/explore/stores").param("businessCategoryId", "0")),
                Arguments.of("음수 업종 ID", get("/explore/stores").param("businessCategoryId", "-1")),
                Arguments.of("숫자가 아닌 업종 ID", get("/explore/stores").param("businessCategoryId", "cafe")),
                Arguments.of("크기 0", get("/explore/stores").param("size", "0")),
                Arguments.of("크기 101", get("/explore/stores").param("size", "101")),
                Arguments.of("숫자가 아닌 크기", get("/explore/stores").param("size", "many")),
                Arguments.of("해석할 수 없는 커서", get("/explore/stores").param("cursor", "abc")),
                Arguments.of("DB 범위 밖 먼 미래 시각의 커서", get("/explore/stores").param("cursor",
                        StoreExploreCursor.of(StoreExploreSort.LATEST, null,
                                LocalDateTime.of(300_000, 1, 1, 0, 0), 1L).encode())),
                Arguments.of("제안·의뢰 탐색 커서", get("/explore/stores").param("cursor", exploreCursor)),
                Arguments.of("업종을 뺀 요청의 업종 커서", get("/explore/stores").param("cursor", categoryCursor)),
                Arguments.of("다른 업종의 커서", get("/explore/stores").param("businessCategoryId", "4")
                        .param("cursor", categoryCursor)),
                Arguments.of("다른 정렬의 커서", get("/explore/stores").param("businessCategoryId", "3")
                        .param("sort", "OLDEST").param("cursor", categoryCursor)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("잘못된 파라미터·커서·조건 불일치는 COMMON_400으로 거부하고 파사드를 호출하지 않는다")
    void rejectsInvalidRequest(String caseName, MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(exploreFacade);
    }

    @Test
    @DisplayName("학생이 아닌 호출자는 STORE_403_STUDENT로 응답한다")
    void returnsForbiddenForNonStudent() throws Exception {
        when(exploreFacade.exploreStores(any()))
                .thenThrow(new BusinessException(ErrorCode.STORE_STUDENT_REQUIRED));

        mockMvc.perform(get("/explore/stores").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("STORE_403_STUDENT"));
    }

    private StoreExploreCommand captureCommand() {
        ArgumentCaptor<StoreExploreCommand> captor = ArgumentCaptor.forClass(StoreExploreCommand.class);
        verify(exploreFacade).exploreStores(captor.capture());
        return captor.getValue();
    }
}
