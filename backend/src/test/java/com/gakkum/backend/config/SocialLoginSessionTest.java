package com.gakkum.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.RestAuthenticationEntryPoint;
import com.gakkum.backend.handler.SocialSuccessHandler;
import com.gakkum.backend.util.JWTUtil;

/**
 * 실제 보안 설정과 SocialSuccessHandler 로 카카오 로그인 전체 흐름(인가 요청 → 콜백)을 MockMvc 로 지난 뒤,
 * 그 세션(JSESSIONID)만 가진 API 요청이 인증되지 않는지 확인한다. 카카오 토큰 발급과 사용자 조회만 흉내 낸다.
 */
@DisplayName("소셜 로그인 뒤 세션으로 인증되지 않음")
@WebMvcTest(controllers = SocialLoginSessionTest.TestController.class)
@Import({SecurityConfig.class, FrontendOrigins.class, RestAuthenticationEntryPoint.class, SocialSuccessHandler.class,
        SocialLoginSessionTest.KakaoStubConfig.class, SocialLoginSessionTest.TestController.class})
@TestPropertySource(properties = "demo-login.enabled=false")
class SocialLoginSessionTest {

    private static final String LOCAL = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JWTUtil jwtUtil;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    @BeforeEach
    void givenKakaoUser() {
        when(userService.loadUser(any(OAuth2UserRequest.class))).thenReturn(new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_PENDING")), Map.of("id", "KAKAO_12345"), "id"));
        when(jwtUtil.createJWT("KAKAO_12345", "ROLE_PENDING", false)).thenReturn("refresh-token");
    }

    @Test
    @DisplayName("카카오 로그인이 끝나면 세션을 끝내 그 세션 쿠키만으로는 API가 401이고, 이동 주소와 60초 refreshToken 쿠키는 그대로다")
    void sessionDoesNotAuthenticateAfterSocialLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();

        MvcResult authorization = mockMvc.perform(get("/oauth2/authorization/kakao")
                .param("redirect_origin", LOCAL)
                .session(session))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        String state = URLDecoder.decode(UriComponentsBuilder
                .fromUriString(authorization.getResponse().getRedirectedUrl()).build()
                .getQueryParams().getFirst("state"), StandardCharsets.UTF_8);

        mockMvc.perform(get("/login/oauth2/code/kakao")
                .param("code", "kakao-code")
                .param("state", state)
                .session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(LOCAL + "/cookie"))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                    containsString("refreshToken=refresh-token"), containsString("Max-Age=60;"),
                    containsString("SameSite=None"))));

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/test/session-login/me").session(session))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 API는 access token(Authorization 헤더)으로는 그대로 인증된다")
    void accessTokenStillAuthenticates() throws Exception {
        when(jwtUtil.isValid("access-token", true)).thenReturn(true);
        when(jwtUtil.getUsername("access-token")).thenReturn("KAKAO_12345");
        when(jwtUtil.getRole("access-token")).thenReturn("ROLE_STUDENT");

        mockMvc.perform(get("/test/session-login/me").header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andExpect(status().isOk());
    }

    @RestController
    static class TestController {

        @GetMapping("/test/session-login/me")
        String me() {
            return "ok";
        }
    }

    /** 카카오 서버 대신 쓰는 등록 정보와 토큰 발급 */
    @TestConfiguration
    static class KakaoStubConfig {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("kakao")
                    .clientId("test-client")
                    .clientSecret("test-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                    .tokenUri("https://kauth.kakao.com/oauth/token")
                    .userInfoUri("https://kapi.kakao.com/v2/user/me")
                    .userNameAttributeName("id")
                    .build());
        }

        @Bean
        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient() {
            return request -> OAuth2AccessTokenResponse.withToken("kakao-access-token")
                    .tokenType(OAuth2AccessToken.TokenType.BEARER)
                    .expiresIn(60)
                    .build();
        }
    }
}
