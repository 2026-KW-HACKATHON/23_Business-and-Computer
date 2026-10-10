package com.gakkum.backend.domain.owner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.SaveStoreConcernCommand;
import com.gakkum.backend.domain.owner.entity.StoreConcern;
import com.gakkum.backend.domain.owner.repository.StoreConcernRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("가게 고민 서비스")
class StoreConcernServiceTest {

    private static final Long OWNER_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");

    private final StoreConcernRepository repository = mock(StoreConcernRepository.class);
    private final StoreConcernService service =
            new StoreConcernService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("해결되지 않은 고민이 없으면 한 줄·설명·분야로 새 고민을 만든다")
    void createsConcernWhenNoneOpen() {
        when(repository.findByOwnerProfileIdAndResolvedAtIsNull(OWNER_ID)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StoreConcern saved = service.saveConcern(OWNER_ID, command("평일 점심 손님이 적어요", "직장인 손님", 1L));

        assertThat(saved.getOwnerProfileId()).isEqualTo(OWNER_ID);
        assertThat(saved.getTitle()).isEqualTo("평일 점심 손님이 적어요");
        assertThat(saved.getDescription()).isEqualTo("직장인 손님");
        assertThat(saved.getSpecialtyCategoryId()).isEqualTo(1L);
        assertThat(saved.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("해결되지 않은 고민이 있으면 새로 만들지 않고 통째로 고치며 설명·분야의 null은 지운다")
    void updatesOpenConcern() {
        StoreConcern open = StoreConcern.builder().id(7L).ownerProfileId(OWNER_ID).title("예전 고민")
                .description("예전 설명").specialtyCategoryId(2L).build();
        when(repository.findByOwnerProfileIdAndResolvedAtIsNull(OWNER_ID)).thenReturn(Optional.of(open));
        when(repository.saveAndFlush(open)).thenReturn(open);

        StoreConcern saved = service.saveConcern(OWNER_ID, command("새 고민", null, null));

        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getTitle()).isEqualTo("새 고민");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getSpecialtyCategoryId()).isNull();
    }

    @Test
    @DisplayName("동시에 처음 고민을 만들어 부분 유니크 인덱스에 걸리면 STORE_CONCERN_409로 거부한다")
    void mapsOpenConcernUniqueViolationToConflict() {
        when(repository.findByOwnerProfileIdAndResolvedAtIsNull(OWNER_ID)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"" + StoreConcern.OPEN_OWNER_UNIQUE_INDEX + "\""));

        assertThatThrownBy(() -> service.saveConcern(OWNER_ID, command("고민", null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORE_CONCERN_CONFLICT));
    }

    @Test
    @DisplayName("부분 유니크 인덱스가 아닌 무결성 오류는 그대로 올린다")
    void rethrowsOtherIntegrityViolation() {
        when(repository.findByOwnerProfileIdAndResolvedAtIsNull(OWNER_ID)).thenReturn(Optional.empty());
        DataIntegrityViolationException other = new DataIntegrityViolationException("value too long");
        when(repository.saveAndFlush(any())).thenThrow(other);

        assertThatThrownBy(() -> service.saveConcern(OWNER_ID, command("고민", null, null))).isSameAs(other);
    }

    @Test
    @DisplayName("해결하면 지우지 않고 현재 시각(UTC)을 해결 시각으로 남긴다")
    void resolvesWithCurrentUtcTime() {
        StoreConcern open = StoreConcern.builder().id(7L).ownerProfileId(OWNER_ID).title("고민").build();
        when(repository.findByOwnerProfileIdAndResolvedAtIsNull(OWNER_ID)).thenReturn(Optional.of(open));

        service.resolveConcern(OWNER_ID);

        ArgumentCaptor<StoreConcern> captor = ArgumentCaptor.forClass(StoreConcern.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getResolvedAt()).isEqualTo(LocalDateTime.of(2026, 10, 10, 3, 0));
    }

    @Test
    @DisplayName("해결되지 않은 고민이 없는데 해결하려 하면 STORE_CONCERN_404로 거부한다")
    void rejectsResolveWithoutOpenConcern() {
        when(repository.findByOwnerProfileIdAndResolvedAtIsNull(OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveConcern(OWNER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORE_CONCERN_NOT_FOUND));
    }

    @Test
    @DisplayName("여러 가게의 해결되지 않은 고민을 한 번에 읽어 가게별로 묶고, 빈 목록이면 조회하지 않는다")
    void groupsOpenConcernsByOwner() {
        StoreConcern first = StoreConcern.builder().id(1L).ownerProfileId(10L).title("가").build();
        StoreConcern second = StoreConcern.builder().id(2L).ownerProfileId(11L).title("나").build();
        when(repository.findAllByOwnerProfileIdInAndResolvedAtIsNull(List.of(10L, 11L, 12L)))
                .thenReturn(List.of(first, second));

        Map<Long, StoreConcern> concerns = service.getOpenConcerns(List.of(10L, 11L, 12L));

        assertThat(concerns).containsOnlyKeys(10L, 11L);
        assertThat(concerns.get(11L).getTitle()).isEqualTo("나");

        StoreConcernRepository untouched = mock(StoreConcernRepository.class);
        assertThat(new StoreConcernService(untouched, Clock.systemUTC()).getOpenConcerns(List.of())).isEmpty();
        verifyNoInteractions(untouched);
    }

    private SaveStoreConcernCommand command(String title, String description, Long categoryId) {
        return SaveStoreConcernCommand.of("KAKAO_12345", title, description, categoryId);
    }
}
