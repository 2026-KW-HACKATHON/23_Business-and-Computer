package com.gakkum.backend.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.gakkum.backend.util.JWTUtil;

@DisplayName("JWT 인증 필터")
class JWTFilterTest {

    private static final String INVALID_TOKEN_BODY = "{\"error\":\"토큰 만료 또는 유효하지 않은 토큰\"}";

    private final JWTUtil jwtUtil = mock(JWTUtil.class);
    private final JWTFilter filter = new JWTFilter(jwtUtil);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = { "access-token", "Basic dXNlcjpwYXNz", "bearer access-token", "Bearer", "Bearer " })
    @DisplayName("Bearer 접두사가 없거나 토큰이 비어 있는 헤더는 예외 없이 유효하지 않은 토큰과 같은 401로 거부하고 다음 필터로 넘기지 않는다")
    void rejectsMalformedHeaderAsUnauthorized(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/students/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isEqualTo(INVALID_TOKEN_BODY);
        assertThat(chain.getRequest()).isNull();
        verify(jwtUtil, never()).isValid(any(), anyBoolean());
    }

    @Test
    @DisplayName("유효하지 않은 Bearer 토큰은 401로 거부하고 다음 필터로 넘기지 않는다")
    void rejectsInvalidBearerToken() throws Exception {
        when(jwtUtil.isValid("expired-token", true)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/students/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isEqualTo(INVALID_TOKEN_BODY);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰은 토큰의 사용자 이름과 역할로 인증하고 다음 필터로 넘긴다")
    void authenticatesValidBearerToken() throws Exception {
        when(jwtUtil.isValid("access-token", true)).thenReturn(true);
        when(jwtUtil.getUsername("access-token")).thenReturn("KAKAO_123");
        when(jwtUtil.getRole("access-token")).thenReturn("STUDENT");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/students/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getName()).isEqualTo("KAKAO_123");
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("STUDENT");
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증하지 않고 다음 필터로 넘긴다")
    void passesThroughWithoutHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/specialties");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
