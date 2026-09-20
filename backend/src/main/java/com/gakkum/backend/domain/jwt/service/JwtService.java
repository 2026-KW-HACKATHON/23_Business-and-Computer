package com.gakkum.backend.domain.jwt.service;

import com.gakkum.backend.domain.jwt.dto.JWTResponseDTO;
import com.gakkum.backend.domain.jwt.dto.RefreshRequestDTO;
import com.gakkum.backend.domain.jwt.entity.RefreshToken;
import com.gakkum.backend.domain.jwt.repository.RefreshRepository;
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

    // 소셜 로그인 성공 후 쿠키(Refresh) -> 헤더 방식으로 응답 <-- 이건 추후에 작성
    @Transactional
    public JWTResponseDTO cookie2Header(
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        // 쿠키 리스트
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new RuntimeException("쿠키가 존재하지 않습니다.");
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
            throw new RuntimeException("refreshToken 쿠키가 없습니다.");
        }

        // Refresh 토큰 검증
        Boolean isValid = jwtUtil.isValid(refreshToken, false);
        if (!isValid) {
            throw new RuntimeException("유효하지 않은 refreshToken입니다.");
        }

        // 정보 추출
        String username = jwtUtil.getUsername(refreshToken);
        String role = jwtUtil.getRole(refreshToken);

        // 토큰 생성
        String newAccessToken = jwtUtil.createJWT(username, role, true);
        String newRefreshToken = jwtUtil.createJWT(username, role, false);

        // 기존 Refresh 토큰 DB 삭제 후 신규 추가
        RefreshToken newRefreshEntity = RefreshToken.builder()
                .username(username)
                .refresh(newRefreshToken)
                .build();

        removeRefresh(refreshToken);
        refreshRepository.flush(); // 같은 트랜잭션 내부라 : 삭제 -> 생성 문제 해결
        refreshRepository.save(newRefreshEntity);

        // 기존 쿠키 제거
        Cookie refreshCookie = new Cookie("refreshToken", null);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(10);
        response.addCookie(refreshCookie);

        // ResponseCookie 방식
        ResponseCookie newCookie = ResponseCookie.from("refreshToken", newRefreshToken)
                .path("/")
                .sameSite("Lax")
                .httpOnly(true)
                .secure(false)  // https 때 true
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
            throw new RuntimeException("쿠키가 존재하지 않습니다.");
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
            throw new RuntimeException("refreshToken 쿠키가 없습니다.");
        }

        // Refresh 토큰 검증
        Boolean isValid = jwtUtil.isValid(refreshToken, false);
        if (!isValid) {
            throw new RuntimeException("유효하지 않은 refreshToken입니다.");
        }

        if (!existsRefresh(refreshToken)) {
            throw new RuntimeException("존재하지 않는 refreshToken입니다.");
        }

        // 정보 추출
        String username = jwtUtil.getUsername(refreshToken);
        String role = jwtUtil.getRole(refreshToken);

        // 토큰 생성
        String newAccessToken = jwtUtil.createJWT(username, role, true);
        String newRefreshToken = jwtUtil.createJWT(username, role, false);

        // 기존 Refresh 토큰 DB 삭제 후 신규 추가
        RefreshToken newRefreshEntity = RefreshToken.builder()
                .username(username)
                .refresh(newRefreshToken)
                .build();

        // 기존 Refresh 토큰 제거
        removeRefresh(refreshToken);
        Cookie expired = new Cookie("refreshToken", null);
        expired.setHttpOnly(true);
        expired.setSecure(false);
        expired.setPath("/");
        expired.setMaxAge(0);
        response.addCookie(expired);

        // 쿠키 세팅
        Cookie newCookie = new Cookie("refreshToken", newRefreshToken);
        newCookie.setHttpOnly(true);
        newCookie.setSecure(false);
        newCookie.setPath("/");
        newCookie.setMaxAge(7 * 24 * 60 * 60);

        response.addCookie(newCookie);

        refreshRepository.flush(); // 같은 트랜잭션 내부라 : 삭제 -> 생성 문제 해결
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

    // JWT Refresh 존재 확인 메소드
    @Transactional(readOnly = true)
    public Boolean existsRefresh(String refreshToken) {
        return refreshRepository.existsByRefresh(refreshToken);
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
}
