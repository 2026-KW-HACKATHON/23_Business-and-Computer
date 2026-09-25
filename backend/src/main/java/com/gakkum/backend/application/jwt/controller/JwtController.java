package com.gakkum.backend.application.jwt.controller;

import com.gakkum.backend.domain.jwt.dto.JWTResponseDTO;
import com.gakkum.backend.domain.jwt.dto.RefreshRequestDTO;
import com.gakkum.backend.domain.jwt.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JwtController {

    private final JwtService jwtService;


    // 소셜 로그인 쿠키 방식의 Refresh 토큰 헤더 방식으로 교환
    @PostMapping(value = "/jwt/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JWTResponseDTO jwtExchangeApi(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return jwtService.cookie2Header(request, response);
    }

    // AccessToken 이 만료되었을 때
    @PostMapping("/refresh")
    public JWTResponseDTO jwtRefreshCookie(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return jwtService.refreshToken(request, response);
    }

}
