package com.gakkum.backend.domain.jwt.service;

import com.gakkum.backend.domain.jwt.dto.JWTResponseDTO;
import com.gakkum.backend.domain.jwt.dto.RefreshRequestDTO;
import com.gakkum.backend.domain.jwt.entity.RefreshToken;
import com.gakkum.backend.domain.jwt.repository.RefreshRepository;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.util.JWTUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {
    private final RefreshRepository refreshRepository;
    private final JWTUtil jwtUtil;

    // 소셜 로그인 성공 후 쿠키(Refresh) -> 헤더 방식으로 응답. 쿠키의 토큰이 DB 에 있어야 교환한다
    @Transactional
    public JWTResponseDTO cookie2Header(
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        // 쿠키 리스트
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // Refresh 토큰 획득
        String refreshToken = null;
        for (Cookie cookie : cookies) {
            if ("refreshToken".equals(cookie.getName())) {
                refreshToken = cookie.getValue();
                break;
            }
        }

        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // Refresh 토큰 검증
        Boolean isValid = jwtUtil.isValid(refreshToken, false);
        if (!isValid) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 로그아웃이나 교체로 DB 에서 지워진 토큰은 서명이 맞아도 거부한다 (/refresh 와 같은 확인)
        if (!consumeRefresh(refreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 정보 추출
        String username = jwtUtil.getUsername(refreshToken);
        String role = jwtUtil.getRole(refreshToken);

        // 토큰 생성
        String newAccessToken = jwtUtil.createJWT(username, role, true);
        String newRefreshToken = jwtUtil.createJWT(username, role, false);

        // 기존 Refresh 토큰은 위에서 지웠으니 신규만 추가
        RefreshToken newRefreshEntity = RefreshToken.builder()
                .username(username)
                .refresh(newRefreshToken)
                .build();

        refreshRepository.save(newRefreshEntity);

        // 새 쿠키로 덮어쓰기
        ResponseCookie newCookie = ResponseCookie.from("refreshToken", newRefreshToken)
                .path("/")
                .sameSite("None")
                .httpOnly(true)
                .secure(true)
                .maxAge(7 * 24 * 60 * 60)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, newCookie.toString());

        return new JWTResponseDTO(newAccessToken);
    }

    @Transactional
    public JWTResponseDTO refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        // 쿠키 리스트
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // Refresh 토큰 획득
        String refreshToken = null;
        for (Cookie cookie : cookies) {
            if ("refreshToken".equals(cookie.getName())) {
                refreshToken = cookie.getValue();
                break;
            }
        }

        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // Refresh 토큰 검증
        Boolean isValid = jwtUtil.isValid(refreshToken, false);
        if (!isValid) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // DB 에 없는 토큰은 거부하고, 있으면 이 자리에서 지운다 (같은 토큰으로 동시에 와도 한 요청만 통과한다)
        if (!consumeRefresh(refreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 정보 추출
        String username = jwtUtil.getUsername(refreshToken);
        String role = jwtUtil.getRole(refreshToken);

        // 토큰 생성
        String newAccessToken = jwtUtil.createJWT(username, role, true);
        String newRefreshToken = jwtUtil.createJWT(username, role, false);

        // 기존 Refresh 토큰은 위에서 지웠으니 신규만 추가
        RefreshToken newRefreshEntity = RefreshToken.builder()
                .username(username)
                .refresh(newRefreshToken)
                .build();

        // 새 쿠키로 덮어쓰기
        ResponseCookie newCookie = ResponseCookie.from("refreshToken", newRefreshToken)
                .path("/")
                .sameSite("None")
                .httpOnly(true)
                .secure(true)
                .maxAge(7 * 24 * 60 * 60)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, newCookie.toString());

        refreshRepository.save(newRefreshEntity);

        return new JWTResponseDTO(newAccessToken);
    }

    // JWT Refresh 토큰 발급 후 저장 메소드
    @Transactional
    public void addRefresh(String username, String refreshToken) {
        RefreshToken entity = RefreshToken.builder()
                .username(username)
                .refresh(refreshToken)
                .build();

        refreshRepository.save(entity);
    }

    /*
     * JWT Refresh 토큰을 한 번만 쓰게 DB 에서 지우고, 지운 행이 있을 때만 true 를 준다.
     * 확인과 삭제를 DELETE 한 번으로 하므로, 같은 토큰으로 동시에 온 요청은 먼저 지운 쪽만 true 를 받고
     * 나머지는 그 트랜잭션이 끝날 때까지 기다린 뒤 false 를 받는다. 호출하는 쪽의 트랜잭션 안에서 실행한다.
     */
    private boolean consumeRefresh(String refreshToken) {
        return refreshRepository.deleteAllByRefresh(refreshToken) > 0;
    }

    // JWT Refresh 토큰 삭제 메소드
    @Transactional
    public void removeRefresh(String refreshToken) {
        refreshRepository.deleteByRefresh(refreshToken);
    }

    // 특정 유저 Refresh 토큰 모두 삭제 (탈퇴)
    @Transactional
    public void removeRefreshUser(String username) {
        refreshRepository.deleteByUsername(username);
    }

    public String issueAccessToken(String username, UserRole role) {
        return jwtUtil.createJWT(username, "ROLE_" + role.name(), true);
    }

    @Transactional
    public String replaceRefreshToken(String username, UserRole role) {
        String refreshToken = jwtUtil.createJWT(username, "ROLE_" + role.name(), false);

        refreshRepository.deleteByUsername(username);
        refreshRepository.flush();
        refreshRepository.save(RefreshToken.builder()
                .username(username)
                .refresh(refreshToken)
                .build());

        return refreshToken;
    }
}
