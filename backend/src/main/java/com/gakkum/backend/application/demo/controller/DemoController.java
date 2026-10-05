package com.gakkum.backend.application.demo.controller;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.demo.dto.DemoLoginRequest;
import com.gakkum.backend.application.demo.dto.DemoLoginResponse;
import com.gakkum.backend.application.demo.facade.DemoFacade;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "demo-login.enabled", havingValue = "true")
public class DemoController {

    private static final Duration REFRESH_TOKEN_MAX_AGE = Duration.ofDays(7);

    private final DemoFacade demoFacade;

    // 로그인 없는 체험용 데모 로그인. JWT 발급 API라 /jwt/exchange 와 같이 공통 응답 봉투 없이 토큰을 반환한다
    @PostMapping(value = "/demo/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DemoLoginResponse login(
            @Valid @RequestBody DemoLoginRequest request,
            HttpServletResponse httpServletResponse) {
        DemoLoginResponse response = demoFacade.login(request);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .path("/")
                .sameSite("None")
                .httpOnly(true)
                .secure(true)
                .maxAge(REFRESH_TOKEN_MAX_AGE)
                .build();
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return response;
    }
}
