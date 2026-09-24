package com.gakkum.backend.application.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.auth.dto.StudentEmailSendRequest;
import com.gakkum.backend.application.auth.dto.StudentEmailVerifyRequest;
import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/student-verification/email")
    public ResponseEntity<ApiResponse<Void>> sendStudentEmailVerification(Authentication authentication,
                                                 @Valid @RequestBody StudentEmailSendRequest request) {
        authService.sendStudentEmailVerification(authentication.getName(), request.normalizedEmail());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/auth/student-verification/email/verify")
    public ResponseEntity<ApiResponse<Void>> verifyStudentEmail(Authentication authentication,
                                                   @Valid @RequestBody StudentEmailVerifyRequest request) {
        authService.verifyStudentEmail(authentication.getName(), request.normalizedEmail(), request.code());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
