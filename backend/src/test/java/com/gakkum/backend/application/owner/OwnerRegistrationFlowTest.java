package com.gakkum.backend.application.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.owner.controller.OwnerRegistrationController;
import com.gakkum.backend.application.owner.facade.OwnerRegistrationFacade;
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

class OwnerRegistrationFlowTest {

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
            .username("KAKAO_12345")
            .isLock(false)
            .role(UserRole.PENDING)
            .build();
    private final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            "KAKAO_12345",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_PENDING")));

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(refreshRepository, jwtUtil);
        UserService userService = new UserService(userRepository, jwtService);
        OwnerService ownerService = new OwnerService(ownerRepository);
        BusinessCategoryService businessCategoryService = new BusinessCategoryService(businessCategoryRepository);
        OwnerRegistrationFacade facade = new OwnerRegistrationFacade(
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
    void registersOwnerThroughControllerFacadeAndDomainServices() throws Exception {
        when(userRepository.findByUsernameAndIsLock("KAKAO_12345", false)).thenReturn(Optional.of(pendingUser));
        when(businessCategoryRepository.existsById(2L)).thenReturn(true);
        when(ownerRepository.save(any(Owner.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.createJWT("KAKAO_12345", "ROLE_OWNER", true)).thenReturn("owner-access-token");
        when(jwtUtil.createJWT("KAKAO_12345", "ROLE_OWNER", false)).thenReturn("owner-refresh-token");

        mockMvc.perform(post("/auth/owner")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("owner-access-token"))
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.allOf(
                    Matchers.containsString("refreshToken=owner-refresh-token"),
                    Matchers.containsString("HttpOnly"),
                    Matchers.containsString("SameSite=Lax"))));

        assertThat(pendingUser.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(pendingUser.getName()).isEqualTo("김사장");

        ArgumentCaptor<Owner> ownerCaptor = ArgumentCaptor.forClass(Owner.class);
        verify(ownerRepository).save(ownerCaptor.capture());
        Owner owner = ownerCaptor.getValue();
        assertThat(owner.getUserId()).isEqualTo(pendingUser.getId());
        assertThat(owner.getBusinessNumber()).isEqualTo("1234567890");
        assertThat(owner.getOpenedAt()).isEqualTo(LocalDate.of(2020, 3, 1));
        assertThat(owner.getCategoryId()).isEqualTo(2L);
        assertThat(owner.getStoreImageUrls()).hasSize(2);

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshRepository).save(refreshTokenCaptor.capture());
        assertThat(refreshTokenCaptor.getValue().getRefresh()).isEqualTo("owner-refresh-token");
    }

    @Test
    void rejectsDuplicateBusinessNumber() throws Exception {
        when(userRepository.findByUsernameAndIsLock("KAKAO_12345", false)).thenReturn(Optional.of(pendingUser));
        when(ownerRepository.existsByBusinessNumber("1234567890")).thenReturn(true);

        mockMvc.perform(post("/auth/owner")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("OWNER_409_BUSINESS_NUMBER"));

        assertThat(pendingUser.getRole()).isEqualTo(UserRole.PENDING);
        verify(ownerRepository, never()).save(any());
    }

    @Test
    void rejectsMalformedOpenedAt() throws Exception {
        mockMvc.perform(post("/auth/owner")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY.replace("2020-03-01", "2020-13-01")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }
}
