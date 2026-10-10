package com.gakkum.backend.domain.jwt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.gakkum.backend.domain.jwt.dto.JWTResponseDTO;
import com.gakkum.backend.domain.jwt.entity.RefreshToken;
import com.gakkum.backend.domain.jwt.repository.RefreshRepository;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.util.JWTUtil;

class JwtServiceTest {

    private final RefreshRepository refreshRepository = mock(RefreshRepository.class);
    private final JWTUtil jwtUtil = mock(JWTUtil.class);
    private final JwtService jwtService = new JwtService(refreshRepository, jwtUtil);

    @Test
    void issuesStudentAccessToken() {
        when(jwtUtil.createJWT("KAKAO_12345", "ROLE_STUDENT", true)).thenReturn("access-token");

        assertThat(jwtService.issueAccessToken("KAKAO_12345", UserRole.STUDENT))
                .isEqualTo("access-token");
    }

    @Test
    void replacesExistingRefreshTokensWithStudentToken() {
        when(jwtUtil.createJWT("KAKAO_12345", "ROLE_STUDENT", false)).thenReturn("refresh-token");

        String token = jwtService.replaceRefreshToken("KAKAO_12345", UserRole.STUDENT);

        assertThat(token).isEqualTo("refresh-token");
        InOrder order = inOrder(refreshRepository);
        order.verify(refreshRepository).deleteByUsername("KAKAO_12345");
        order.verify(refreshRepository).flush();
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        order.verify(refreshRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUsername()).isEqualTo("KAKAO_12345");
        assertThat(tokenCaptor.getValue().getRefresh()).isEqualTo("refresh-token");
        verify(jwtUtil).createJWT("KAKAO_12345", "ROLE_STUDENT", false);
    }

    @Test
    @DisplayName("토큰 교환 시 기존 토큰을 교체하고 7일짜리 크로스 사이트 쿠키 하나만 내려준다")
    void exchangeReplacesTokenAndSetsSingleCrossSiteCookie() {
        givenValidOldToken();
        when(refreshRepository.existsByRefresh("old-refresh")).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        JWTResponseDTO result = jwtService.cookie2Header(requestWithRefreshCookie("old-refresh"), response);

        assertThat(result).isEqualTo(new JWTResponseDTO("new-access"));
        assertTokenReplaced();
        assertSingleRefreshCookie(response);
    }

    @Test
    @DisplayName("토큰 재발급 시 기존 토큰을 교체하고 7일짜리 크로스 사이트 쿠키 하나만 내려준다")
    void refreshReplacesTokenAndSetsSingleCrossSiteCookie() {
        givenValidOldToken();
        when(refreshRepository.existsByRefresh("old-refresh")).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        JWTResponseDTO result = jwtService.refreshToken(requestWithRefreshCookie("old-refresh"), response);

        assertThat(result).isEqualTo(new JWTResponseDTO("new-access"));
        assertTokenReplaced();
        assertSingleRefreshCookie(response);
    }

    @Test
    @DisplayName("쿠키가 없으면 토큰 교환과 재발급을 거부한다")
    void rejectsWhenCookieMissing() {
        MockHttpServletRequest noCookies = new MockHttpServletRequest();
        MockHttpServletRequest otherCookie = new MockHttpServletRequest();
        otherCookie.setCookies(new Cookie("other", "value"));

        assertRejected(() -> jwtService.cookie2Header(noCookies, new MockHttpServletResponse()));
        assertRejected(() -> jwtService.refreshToken(noCookies, new MockHttpServletResponse()));
        assertRejected(() -> jwtService.cookie2Header(otherCookie, new MockHttpServletResponse()));
        assertRejected(() -> jwtService.refreshToken(otherCookie, new MockHttpServletResponse()));
    }

    @Test
    @DisplayName("유효하지 않은 refreshToken이면 토큰 교환과 재발급을 거부한다")
    void rejectsInvalidRefreshToken() {
        when(jwtUtil.isValid("bad-refresh", false)).thenReturn(false);

        assertRejected(() -> jwtService.cookie2Header(
                requestWithRefreshCookie("bad-refresh"), new MockHttpServletResponse()));
        assertRejected(() -> jwtService.refreshToken(
                requestWithRefreshCookie("bad-refresh"), new MockHttpServletResponse()));
    }

    @Test
    @DisplayName("DB에 등록되지 않은 refreshToken이면 재발급을 거부한다")
    void rejectsUnregisteredRefreshTokenOnRefresh() {
        when(jwtUtil.isValid("old-refresh", false)).thenReturn(true);
        when(refreshRepository.existsByRefresh("old-refresh")).thenReturn(false);

        assertRejected(() -> jwtService.refreshToken(
                requestWithRefreshCookie("old-refresh"), new MockHttpServletResponse()));
    }

    @Test
    @DisplayName("서명이 맞아도 DB에서 지워진 refreshToken이면 토큰 교환을 거부하고 쿠키를 내려주지 않는다")
    void rejectsUnregisteredRefreshTokenOnExchange() {
        when(jwtUtil.isValid("old-refresh", false)).thenReturn(true);
        when(refreshRepository.existsByRefresh("old-refresh")).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertRejected(() -> jwtService.cookie2Header(requestWithRefreshCookie("old-refresh"), response));
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        verify(refreshRepository, never()).deleteByRefresh(any());
    }

    private void givenValidOldToken() {
        when(jwtUtil.isValid("old-refresh", false)).thenReturn(true);
        when(jwtUtil.getUsername("old-refresh")).thenReturn("KAKAO_12345");
        when(jwtUtil.getRole("old-refresh")).thenReturn("ROLE_STUDENT");
        when(jwtUtil.createJWT("KAKAO_12345", "ROLE_STUDENT", true)).thenReturn("new-access");
        when(jwtUtil.createJWT("KAKAO_12345", "ROLE_STUDENT", false)).thenReturn("new-refresh");
    }

    private MockHttpServletRequest requestWithRefreshCookie(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", value));
        return request;
    }

    private void assertTokenReplaced() {
        InOrder order = inOrder(refreshRepository);
        order.verify(refreshRepository).deleteByRefresh("old-refresh");
        order.verify(refreshRepository).flush();
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        order.verify(refreshRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUsername()).isEqualTo("KAKAO_12345");
        assertThat(tokenCaptor.getValue().getRefresh()).isEqualTo("new-refresh");
    }

    private void assertSingleRefreshCookie(MockHttpServletResponse response) {
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).singleElement().asString()
                .contains("refreshToken=new-refresh", "Path=/", "Max-Age=604800", "Secure", "HttpOnly",
                        "SameSite=None")
                .doesNotContain("Domain");
    }

    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(refreshRepository, never()).save(any());
    }
}
