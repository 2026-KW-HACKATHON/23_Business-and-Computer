package com.gakkum.backend.domain.jwt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.gakkum.backend.domain.jwt.entity.RefreshToken;
import com.gakkum.backend.domain.jwt.repository.RefreshRepository;
import com.gakkum.backend.domain.user.entity.UserRole;
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
}
