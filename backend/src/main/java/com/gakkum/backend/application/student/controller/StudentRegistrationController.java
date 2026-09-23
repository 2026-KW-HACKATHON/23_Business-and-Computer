package com.gakkum.backend.application.student.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.student.dto.StudentRegistrationRequest;
import com.gakkum.backend.application.student.dto.StudentRegistrationResponse;
import com.gakkum.backend.application.student.facade.StudentRegistrationFacade;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class StudentRegistrationController {

    private static final Duration REFRESH_TOKEN_MAX_AGE = Duration.ofDays(7);

    private final StudentRegistrationFacade studentRegistrationFacade;

    @PostMapping("/auth/student")
    public ApiResponse<StudentRegistrationResponse> register(
            Authentication authentication,
            @Valid @RequestBody StudentRegistrationRequest request,
            HttpServletResponse httpServletResponse) {
        StudentRegistrationResponse response = studentRegistrationFacade.register(
                authentication.getName(),
                request);

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
