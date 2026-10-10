package com.gakkum.backend.domain.owner.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.SaveStoreConcernCommand;
import com.gakkum.backend.domain.owner.entity.StoreConcern;
import com.gakkum.backend.domain.owner.service.StoreConcernService;

import jakarta.persistence.EntityManager;

/**
 * 가게마다 해결되지 않은 고민을 하나로 막는 부분 유니크 인덱스(V49)는 PostgreSQL에서만 확인할 수 있어 실제 DB로 검증한다.
 * 공용 DB에 테스트 행을 쓰지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 트랜잭션은 테스트마다 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("가게 고민 PostgreSQL 저장 (해결되지 않은 고민 하나·해결 기록)")
class StoreConcernPostgresTest {

    // 실제 매장과 겹치지 않는 사장님 프로필 ID. 물리 FK가 없어 프로필 행 없이 저장할 수 있다
    private static final Long OWNER_ID = 9_000_000_001L;
    private static final Long OTHER_OWNER_ID = 9_000_000_002L;

    @Autowired
    private StoreConcernRepository storeConcernRepository;

    @Autowired
    private EntityManager entityManager;

    private StoreConcernService service;

    @BeforeEach
    void setUp() {
        service = new StoreConcernService(storeConcernRepository, Clock.systemUTC());
    }

    @Test
    @DisplayName("같은 가게에 해결되지 않은 고민을 두 개 저장하면 부분 유니크 인덱스가 막는다")
    void blocksSecondOpenConcern() {
        storeConcernRepository.saveAndFlush(StoreConcern.create(OWNER_ID, "첫 고민", null, null));

        assertThatThrownBy(() -> storeConcernRepository.saveAndFlush(StoreConcern.create(OWNER_ID, "둘째 고민", null, null)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(StoreConcern.OPEN_OWNER_UNIQUE_INDEX);
    }

    @Test
    @DisplayName("해결한 고민은 남고 같은 가게에 새 고민을 올릴 수 있으며, 탐색에는 해결되지 않은 고민만 나온다")
    void keepsResolvedConcernAndAllowsNewOne() {
        service.saveConcern(OWNER_ID, command("첫 고민"));
        service.resolveConcern(OWNER_ID);
        StoreConcern second = service.saveConcern(OWNER_ID, command("둘째 고민"));
        service.saveConcern(OTHER_OWNER_ID, command("다른 가게 고민"));
        entityManager.clear();

        List<StoreConcern> mine = storeConcernRepository.findAll().stream()
                .filter(concern -> concern.getOwnerProfileId().equals(OWNER_ID))
                .toList();
        assertThat(mine).hasSize(2);
        assertThat(mine).filteredOn(concern -> concern.getResolvedAt() != null)
                .extracting(StoreConcern::getTitle).containsExactly("첫 고민");

        Map<Long, StoreConcern> open = service.getOpenConcerns(List.of(OWNER_ID, OTHER_OWNER_ID));
        assertThat(open.get(OWNER_ID).getId()).isEqualTo(second.getId());
        assertThat(open.get(OTHER_OWNER_ID).getTitle()).isEqualTo("다른 가게 고민");
    }

    @Test
    @DisplayName("해결되지 않은 고민을 다시 저장하면 새 행 없이 같은 행을 고친다")
    void updatesSameRow() {
        StoreConcern first = service.saveConcern(OWNER_ID, command("첫 고민"));
        StoreConcern updated = service.saveConcern(OWNER_ID, command("고친 고민"));
        entityManager.clear();

        assertThat(updated.getId()).isEqualTo(first.getId());
        assertThat(storeConcernRepository.findByOwnerProfileIdAndResolvedAtIsNull(OWNER_ID))
                .get().extracting(StoreConcern::getTitle).isEqualTo("고친 고민");
    }

    private SaveStoreConcernCommand command(String title) {
        return SaveStoreConcernCommand.of("KAKAO_12345", title, null, null);
    }
}
