package com.gakkum.backend.application.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.owner.controller.OwnerRegistrationController;
import com.gakkum.backend.application.owner.facade.OwnerFacade;
import com.gakkum.backend.domain.category.repository.BusinessCategoryRepository;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.jwt.entity.RefreshToken;
import com.gakkum.backend.domain.jwt.repository.RefreshRepository;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;
import com.gakkum.backend.util.JWTUtil;

@DisplayName("사장님 회원가입 전체 흐름 (POST /auth/owner)")
class OwnerRegistrationFlowTest {

    private static final String USERNAME = "KAKAO_12345";

    private static final String REQUEST_BODY = """
            {
              "name": "김사장",
              "storeName": "치킨플러스",
              "storeAddress": "서울시 월계1동 광운로23",
              "categoryId": 2,
              "businessNumber": "123-45-67890",
              "openedAt": "2020-03-01",
              "representativeName": "김사장",
              "description": "매장 한 줄 소개",
              "storeImageUrls": ["https://image.example.com/store1.png", "https://image.example.com/store2.png"],
              "profileImageUrl": "https://image.example.com/profile.png"
            }
            """;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final BusinessCategoryRepository businessCategoryRepository = mock(BusinessCategoryRepository.class);
    private final RefreshRepository refreshRepository = mock(RefreshRepository.class);
    private final JWTUtil jwtUtil = mock(JWTUtil.class);

    private final User pendingUser = User.builder()
            .id("01K58M6PJV8VAJMXHBHJ2PNB5C")
            .username(USERNAME)
            .isLock(false)
            .role(UserRole.PENDING)
            .build();
    private final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            USERNAME,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_PENDING")));

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(refreshRepository, jwtUtil);
        UserService userService = new UserService(userRepository, jwtService);
        OwnerService ownerService = new OwnerService(ownerRepository);
        BusinessCategoryService businessCategoryService = new BusinessCategoryService(businessCategoryRepository);
        OwnerFacade facade = new OwnerFacade(
                userService,
                ownerService,
                businessCategoryService,
                jwtService);
        OwnerRegistrationController controller = new OwnerRegistrationController(facade);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("정상 요청이면 사장님 회원가입이 완료되고 토큰이 발급된다")
    void registersOwnerThroughControllerFacadeAndDomainServices() throws Exception {
        givenPendingUser();
        when(businessCategoryRepository.existsById(2L)).thenReturn(true);
        when(ownerRepository.save(any(Owner.class))).thenAnswer(invocation -> invocation.getArgument(0));
        givenTokens();

        register(REQUEST_BODY)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("owner-access-token"))
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
            .andExpect(jsonPath("$.error").isEmpty())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.allOf(
                    Matchers.containsString("refreshToken=owner-refresh-token"),
                    Matchers.containsString("HttpOnly"),
                    Matchers.containsString("SameSite=Lax"),
                    Matchers.containsString("Path=/"))));

        assertThat(pendingUser.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(pendingUser.getName()).isEqualTo("김사장");
        assertThat(pendingUser.getEmail()).isNull();

        ArgumentCaptor<Owner> ownerCaptor = ArgumentCaptor.forClass(Owner.class);
        verify(ownerRepository).save(ownerCaptor.capture());
        Owner owner = ownerCaptor.getValue();
        assertThat(owner.getUserId()).isEqualTo(pendingUser.getId());
        assertThat(owner.getStoreName()).isEqualTo("치킨플러스");
        assertThat(owner.getStoreAddress()).isEqualTo("서울시 월계1동 광운로23");
        assertThat(owner.getCategoryId()).isEqualTo(2L);
        assertThat(owner.getBusinessNumber()).isEqualTo("1234567890");
        assertThat(owner.getOpenedAt()).isEqualTo(LocalDate.of(2020, 3, 1));
        assertThat(owner.getRepresentativeName()).isEqualTo("김사장");
        assertThat(owner.getDescription()).isEqualTo("매장 한 줄 소개");
        assertThat(owner.getStoreImageUrls())
                .containsExactly("https://image.example.com/store1.png", "https://image.example.com/store2.png");
        assertThat(owner.getProfileImageUrl()).isEqualTo("https://image.example.com/profile.png");

        verify(ownerRepository).existsByBusinessNumber("1234567890");
        verify(refreshRepository).deleteByUsername(USERNAME);
        verify(refreshRepository).flush();
        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshRepository).save(refreshTokenCaptor.capture());
        assertThat(refreshTokenCaptor.getValue().getRefresh()).isEqualTo("owner-refresh-token");
    }

    @Test
    @DisplayName("필수값만 보내도 회원가입되고 선택값은 null, 매장 이미지는 빈 목록으로 저장된다")
    void registersOwnerWithOnlyRequiredFields() throws Exception {
        givenPendingUser();
        when(businessCategoryRepository.existsById(2L)).thenReturn(true);
        when(ownerRepository.save(any(Owner.class))).thenAnswer(invocation -> invocation.getArgument(0));
        givenTokens();

        register("""
                {
                  "name": "김사장",
                  "storeName": "치킨플러스",
                  "categoryId": 2,
                  "businessNumber": "1234567890"
                }
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("owner-access-token"));

        ArgumentCaptor<Owner> ownerCaptor = ArgumentCaptor.forClass(Owner.class);
        verify(ownerRepository).save(ownerCaptor.capture());
        Owner owner = ownerCaptor.getValue();
        assertThat(owner.getBusinessNumber()).isEqualTo("1234567890");
        assertThat(owner.getStoreAddress()).isNull();
        assertThat(owner.getOpenedAt()).isNull();
        assertThat(owner.getRepresentativeName()).isNull();
        assertThat(owner.getDescription()).isNull();
        assertThat(owner.getProfileImageUrl()).isNull();
        assertThat(owner.getStoreImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않거나 잠긴 사용자는 401을 반환한다")
    void rejectsUnknownOrLockedUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        register(REQUEST_BODY)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(ownerRepository, businessCategoryRepository, refreshRepository);
    }

    @Test
    @DisplayName("이미 가입한 사용자는 409를 반환하고 역할이 바뀌지 않는다")
    void rejectsAlreadyRegisteredUser() throws Exception {
        User student = User.builder()
                .id("01K58M6PJV8VAJMXHBHJ2PNB5C")
                .username(USERNAME)
                .isLock(false)
                .role(UserRole.STUDENT)
                .build();
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(student));

        register(REQUEST_BODY)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("USER_409_REGISTERED"));

        assertThat(student.getRole()).isEqualTo(UserRole.STUDENT);
        verifyNoInteractions(ownerRepository, businessCategoryRepository, refreshRepository);
    }

    @Test
    @DisplayName("이미 등록된 사업자등록번호면 409를 반환하고 저장하지 않는다")
    void rejectsDuplicateBusinessNumber() throws Exception {
        givenPendingUser();
        when(ownerRepository.existsByBusinessNumber("1234567890")).thenReturn(true);

        register(REQUEST_BODY)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("OWNER_409_BUSINESS_NUMBER"))
            .andExpect(jsonPath("$.error.message").value("이미 사용 중인 사업자등록번호입니다."));

        assertThat(pendingUser.getRole()).isEqualTo(UserRole.PENDING);
        verify(ownerRepository, never()).save(any());
        verifyNoInteractions(businessCategoryRepository, refreshRepository);
    }

    @Test
    @DisplayName("존재하지 않는 업종이면 400을 반환하고 저장하지 않는다")
    void rejectsUnknownCategory() throws Exception {
        givenPendingUser();
        when(businessCategoryRepository.existsById(2L)).thenReturn(false);

        register(REQUEST_BODY)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("CATEGORY_400"))
            .andExpect(jsonPath("$.error.message").value("존재하지 않는 업종입니다."));

        assertThat(pendingUser.getRole()).isEqualTo(UserRole.PENDING);
        verify(ownerRepository, never()).save(any());
        verifyNoInteractions(refreshRepository);
    }

    @Test
    @DisplayName("저장 시 DB 유니크 제약에 걸리면 409를 반환하고 토큰을 발급하지 않는다")
    void returnsConflictWhenUniqueConstraintIsViolatedOnSave() throws Exception {
        givenPendingUser();
        when(businessCategoryRepository.existsById(2L)).thenReturn(true);
        when(ownerRepository.save(any(Owner.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        register(REQUEST_BODY)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("COMMON_409"));

        verifyNoInteractions(refreshRepository);
    }

    @Test
    @DisplayName("사업자등록번호 형식이 잘못되면 400을 반환하고 DB를 조회하지 않는다")
    void rejectsInvalidRequestBeforeTouchingRepositories() throws Exception {
        register(REQUEST_BODY.replace("\"businessNumber\": \"123-45-67890\"", "\"businessNumber\": \"12341453312\""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, ownerRepository, businessCategoryRepository, refreshRepository);
    }

    @Test
    @DisplayName("필수값이 없으면 400을 반환한다")
    void rejectsMissingRequiredFields() throws Exception {
        register("{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, ownerRepository, businessCategoryRepository, refreshRepository);
    }

    @Test
    @DisplayName("존재하지 않는 개업일이면 400을 반환한다")
    void rejectsMalformedOpenedAt() throws Exception {
        register(REQUEST_BODY.replace("2020-03-01", "2020-13-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, ownerRepository, businessCategoryRepository, refreshRepository);
    }

    @Test
    @DisplayName("JSON 형식이 깨졌으면 400을 반환한다")
    void rejectsMalformedJson() throws Exception {
        register("{\"name\": ")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));

        verifyNoInteractions(userRepository, ownerRepository, businessCategoryRepository, refreshRepository);
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/auth/owner")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void givenPendingUser() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(pendingUser));
    }

    private void givenTokens() {
        when(jwtUtil.createJWT(USERNAME, "ROLE_OWNER", true)).thenReturn("owner-access-token");
        when(jwtUtil.createJWT(USERNAME, "ROLE_OWNER", false)).thenReturn("owner-refresh-token");
    }
}
