package com.gakkum.backend.application.owner.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.owner.dto.OwnerRegistrationRequest;
import com.gakkum.backend.application.owner.dto.OwnerRegistrationResponse;
import com.gakkum.backend.application.owner.facade.OwnerRegistrationFacade;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class OwnerRegistrationController {

    private static final Duration REFRESH_TOKEN_MAX_AGE = Duration.ofDays(7);

    private final OwnerRegistrationFacade ownerRegistrationFacade;

    @PostMapping("/auth/owner")
    public ApiResponse<OwnerRegistrationResponse> register(
            Authentication authentication,
            @Valid @RequestBody OwnerRegistrationRequest request,
            HttpServletResponse httpServletResponse) {
        OwnerRegistrationResponse response = ownerRegistrationFacade.register(
                authentication.getName(),
                request.toCommand());

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .path("/")
                .sameSite("Lax")
                .httpOnly(true)
                .secure(false)
                .maxAge(REFRESH_TOKEN_MAX_AGE)
                .build();
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ApiResponse.success(response);
    }
}
