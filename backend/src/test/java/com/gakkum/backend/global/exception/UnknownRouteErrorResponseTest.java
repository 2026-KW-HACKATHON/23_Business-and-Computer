package com.gakkum.backend.global.exception;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import com.gakkum.backend.config.SecurityConfig;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.response.ApiResponse;
import com.gakkum.backend.util.JWTUtil;

@DisplayName("핸들러가 없는 요청의 공통 오류 응답 검증")
@WebMvcTest(controllers = UnknownRouteErrorResponseTest.TestController.class)
// 테스트 클래스의 중첩 컨트롤러는 컴포넌트 스캔에서 제외되므로 직접 등록한다
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, UnknownRouteErrorResponseTest.TestController.class})
class UnknownRouteErrorResponseTest {

    private static final String ACCESS_TOKEN = "valid-access-token";
    private static final String BEARER = "Bearer " + ACCESS_TOKEN;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private JWTUtil jwtUtil;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    @MockitoBean(name = "SocialSuccessHandler")
    private AuthenticationSuccessHandler socialSuccessHandler;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @BeforeEach
    void setUp() {
        // 운영 서버와 같이 JWTFilter가 시큐리티 필터 체인 안에서만 실행되도록 체인만 등록한다
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(springSecurityFilterChain)
            .build();

        // 실제 JWTFilter를 거쳐 인증되도록 유효한 access token만 흉내 낸다
        given(jwtUtil.isValid(ACCESS_TOKEN, true)).willReturn(true);
        given(jwtUtil.getUsername(ACCESS_TOKEN)).willReturn("kakao_1");
        given(jwtUtil.getRole(ACCESS_TOKEN)).willReturn("ROLE_STUDENT");
    }

    @Test
    @DisplayName("인증된 사용자가 없는 경로로 POST 요청하면 공통 404 응답 형식으로 반환된다")
    void authenticatedPostToUnknownPathReturnsCommonNotFoundResponse() throws Exception {
        mockMvc.perform(post("/no-such-path")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.error.code").value("COMMON_404"))
            .andExpect(jsonPath("$.error.message").value("요청한 경로를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("인증된 사용자가 없는 경로로 GET 요청하면 404를 반환한다")
    void authenticatedGetToUnknownPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/no-such-path").header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_404"));
    }

    @Test
    @DisplayName("인증 없이 없는 경로로 요청하면 경로 존재 여부와 무관하게 401을 반환한다")
    void unauthenticatedRequestToUnknownPathReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/no-such-path")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증된 사용자가 지원하지 않는 HTTP 메서드로 요청하면 Allow 헤더와 함께 405를 반환한다")
    void authenticatedRequestWithUnsupportedMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/test/known").header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.error.code").value("COMMON_405"));
    }

    @Test
    @DisplayName("인증된 사용자가 지원하지 않는 Content-Type으로 요청하면 415를 반환한다")
    void authenticatedRequestWithUnsupportedMediaTypeReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/test/json-only")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.TEXT_PLAIN)
                .content("plain text"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.error.code").value("COMMON_415"));
    }

    @Test
    @DisplayName("인증된 사용자가 존재하는 경로로 요청하면 정상 응답한다")
    void authenticatedRequestToKnownPathSucceeds() throws Exception {
        mockMvc.perform(get("/test/known").header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/known")
        ApiResponse<String> known() {
            return ApiResponse.success("known");
        }

        @PostMapping(value = "/test/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        ApiResponse<Void> jsonOnly(@RequestBody TestRequest request) {
            return ApiResponse.success();
        }
    }

    record TestRequest(String name) {
    }
}
