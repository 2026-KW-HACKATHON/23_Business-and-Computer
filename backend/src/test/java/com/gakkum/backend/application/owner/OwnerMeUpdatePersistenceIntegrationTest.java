package com.gakkum.backend.application.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.gakkum.backend.application.owner.dto.OwnerMeUpdateRequest;
import com.gakkum.backend.application.owner.facade.OwnerFacade;
import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.domain.category.entity.BusinessCategory;
import com.gakkum.backend.domain.category.repository.BusinessCategoryRepository;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.dto.OwnerQueryDto.OwnerMeResult;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * 수정한 값이 실제 UPDATE로 반영되는지와 다른 사장님·수정 대상 외 컬럼이 그대로인지는 실제 DB로 검증한다.
 * 공용 DB에 테스트 행을 쓰지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 트랜잭션은 테스트마다 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ OwnerFacade.class, OwnerService.class, BusinessCategoryService.class })
@DisplayName("사장님 내 정보 수정 PostgreSQL 통합 (저장 반영·선택 항목 삭제·다른 사장님 보존)")
class OwnerMeUpdatePersistenceIntegrationTest {

    private static final String USERNAME = "TEST_ME_UPDATE_OWNER";
    private static final String OTHER_USERNAME = "TEST_ME_UPDATE_OTHER_OWNER";
    private static final LocalDate OPENED_AT = LocalDate.of(2020, 3, 1);
    private static final List<String> STORE_IMAGE_URLS =
            List.of("https://example.com/store1.png", "https://example.com/store2.png");

    @Autowired
    private OwnerFacade ownerFacade;

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private BusinessCategoryRepository businessCategoryRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private ProposalService proposalService;

    @MockitoBean
    private AuthService authService;

    // 본인 업로드 확인은 흐름 테스트에서 검증하고, 여기서는 저장 반영만 본다
    @MockitoBean
    private MediaService mediaService;

    private Long cafe;
    private Long restaurant;
    private Long ownerProfileId;
    private Long otherOwnerProfileId;
    private String businessNumber;
    private String otherBusinessNumber;

    @BeforeEach
    void setUp() {
        cafe = category();
        restaurant = category();
        businessNumber = unique();
        otherBusinessNumber = unique();
        ownerProfileId = owner(USERNAME, businessNumber, "예전 상호");
        otherOwnerProfileId = owner(OTHER_USERNAME, otherBusinessNumber, "다른 매장");
        entityManager.clear();
    }

    @Test
    @DisplayName("PostgreSQL에서 다섯 항목을 저장하면 다시 읽은 프로필과 내 정보 조회에 반영되고 수정 대상이 아닌 컬럼은 그대로다")
    void persistsEditableFields() {
        update(USERNAME, "  가꿈 베이커리  ", restaurant, "https://example.com/profile.png",
                "  서울시 노원구 광운로 1  ", "  매일 굽는 빵집입니다.  ");
        flushAndClear();

        Owner stored = ownerRepository.findById(ownerProfileId).orElseThrow();
        assertThat(stored.getStoreName()).isEqualTo("가꿈 베이커리");
        assertThat(stored.getCategoryId()).isEqualTo(restaurant);
        assertThat(stored.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
        assertThat(stored.getStoreAddress()).isEqualTo("서울시 노원구 광운로 1");
        assertThat(stored.getDescription()).isEqualTo("매일 굽는 빵집입니다.");
        assertUneditableColumnsKept(stored, businessNumber);

        OwnerMeResult me = ownerFacade.getMe(USERNAME);
        assertThat(me.getOwnerProfileId()).isEqualTo(ownerProfileId);
        assertThat(me.getName()).isEqualTo("김가입");
        assertThat(me.getStoreName()).isEqualTo("가꿈 베이커리");
        assertThat(me.getCategoryId()).isEqualTo(restaurant);
        assertThat(me.getDescription()).isEqualTo("매일 굽는 빵집입니다.");
    }

    @Test
    @DisplayName("PostgreSQL에서 선택 항목을 비워 저장하면 본인의 사진·주소·소개만 NULL이 되고 다른 사장님 프로필은 그대로다")
    void clearsOnlyOwnOptionalFields() {
        update(USERNAME, "가꿈 베이커리", restaurant, "", "   ", null);
        flushAndClear();

        Owner stored = ownerRepository.findById(ownerProfileId).orElseThrow();
        assertThat(stored.getStoreName()).isEqualTo("가꿈 베이커리");
        assertThat(stored.getCategoryId()).isEqualTo(restaurant);
        assertThat(stored.getProfileImageUrl()).isNull();
        assertThat(stored.getStoreAddress()).isNull();
        assertThat(stored.getDescription()).isNull();
        assertUneditableColumnsKept(stored, businessNumber);

        Owner other = ownerRepository.findById(otherOwnerProfileId).orElseThrow();
        assertThat(other.getStoreName()).isEqualTo("다른 매장");
        assertThat(other.getCategoryId()).isEqualTo(cafe);
        assertThat(other.getProfileImageUrl()).isEqualTo("https://example.com/old.png");
        assertThat(other.getStoreAddress()).isEqualTo("서울시 노원구 광운로 20");
        assertThat(other.getDescription()).isEqualTo("예전 소개");
        assertUneditableColumnsKept(other, otherBusinessNumber);
    }

    @Test
    @DisplayName("PostgreSQL에서 같은 내용을 반복 저장해도 프로필 행이 늘지 않고 값이 같다")
    void savesSameContentRepeatedly() {
        long before = ownerRepository.count();

        for (int attempt = 0; attempt < 3; attempt++) {
            update(USERNAME, "가꿈 베이커리", restaurant, "https://example.com/profile.png", "서울시 노원구 광운로 1",
                    "소개");
            flushAndClear();
        }

        assertThat(ownerRepository.count()).isEqualTo(before);
        Owner stored = ownerRepository.findById(ownerProfileId).orElseThrow();
        assertThat(stored.getStoreName()).isEqualTo("가꿈 베이커리");
        assertThat(stored.getCategoryId()).isEqualTo(restaurant);
        assertThat(stored.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
        assertThat(stored.getStoreAddress()).isEqualTo("서울시 노원구 광운로 1");
        assertThat(stored.getDescription()).isEqualTo("소개");
    }

    @Test
    @DisplayName("PostgreSQL에 없는 업종 ID로 저장하면 CATEGORY_400으로 거부하고 저장된 프로필은 바뀌지 않는다")
    void rejectsMissingCategoryWithoutWriting() {
        Long missingCategoryId = category();
        businessCategoryRepository.deleteById(missingCategoryId);
        flushAndClear();

        assertThatThrownBy(() -> update(USERNAME, "새 상호", missingCategoryId, "https://example.com/new.png",
                "새 주소", "새 소개"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_CATEGORY_NOT_FOUND));
        flushAndClear();

        Owner stored = ownerRepository.findById(ownerProfileId).orElseThrow();
        assertThat(stored.getStoreName()).isEqualTo("예전 상호");
        assertThat(stored.getCategoryId()).isEqualTo(cafe);
        assertThat(stored.getProfileImageUrl()).isEqualTo("https://example.com/old.png");
        assertThat(stored.getStoreAddress()).isEqualTo("서울시 노원구 광운로 20");
        assertThat(stored.getDescription()).isEqualTo("예전 소개");
    }

    private void update(String username, String storeName, Long categoryId, String profileImageUrl,
            String storeAddress, String description) {
        ownerFacade.updateMe(OwnerMeUpdateRequest.of(
                storeName, categoryId, profileImageUrl, storeAddress, description).toCommand(username));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void assertUneditableColumnsKept(Owner stored, String expectedBusinessNumber) {
        assertThat(stored.getBusinessNumber()).isEqualTo(expectedBusinessNumber);
        assertThat(stored.getOpenedAt()).isEqualTo(OPENED_AT);
        assertThat(stored.getRepresentativeName()).isEqualTo("김대표");
        assertThat(stored.getStoreImageUrls()).isEqualTo(STORE_IMAGE_URLS);
        assertThat(stored.getDemoSessionId()).isNull();
    }

    private Long category() {
        return businessCategoryRepository.saveAndFlush(
                BusinessCategory.builder().name("내 정보 수정 테스트 업종 " + UUID.randomUUID()).build()).getId();
    }

    // 사장님 프로필이 있는 활성 사장님으로 로그인한 상태를 만든다
    private Long owner(String username, String ownerBusinessNumber, String storeName) {
        String userId = unique().substring(0, 26);
        Long id = ownerRepository.saveAndFlush(Owner.create(userId, ownerBusinessNumber, OPENED_AT, "김대표",
                storeName, cafe, "서울시 노원구 광운로 20", "예전 소개", "https://example.com/old.png",
                STORE_IMAGE_URLS, null)).getId();
        when(userService.getActiveUser(username)).thenReturn(
                User.builder().id(userId).username(username).role(UserRole.OWNER).name("김가입").build());
        return id;
    }

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
