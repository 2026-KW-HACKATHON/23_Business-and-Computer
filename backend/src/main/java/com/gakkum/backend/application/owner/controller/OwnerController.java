package com.gakkum.backend.application.owner.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.owner.dto.OwnerMeResponse;
import com.gakkum.backend.application.owner.dto.OwnerMeUpdateRequest;
import com.gakkum.backend.application.owner.dto.OwnerRegistrationRequest;
import com.gakkum.backend.application.owner.dto.OwnerRegistrationResponse;
import com.gakkum.backend.application.owner.dto.StoreConcernResponse;
import com.gakkum.backend.application.owner.dto.StoreConcernSaveRequest;
import com.gakkum.backend.application.owner.facade.OwnerFacade;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class OwnerController {

    private static final Duration REFRESH_TOKEN_MAX_AGE = Duration.ofDays(7);

    private final OwnerFacade ownerFacade;

    @PostMapping("/auth/owner")
    public ResponseEntity<ApiResponse<OwnerRegistrationResponse>> register(
            Authentication authentication,
            @Valid @RequestBody OwnerRegistrationRequest request,
            HttpServletResponse httpServletResponse) {
        OwnerRegistrationResponse response = ownerFacade.register(
                authentication.getName(),
                request);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .path("/")
                .sameSite("None")
                .httpOnly(true)
                .secure(true)
                .maxAge(REFRESH_TOKEN_MAX_AGE)
                .build();
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/owners/me")
    public ResponseEntity<ApiResponse<OwnerMeResponse>> getMe(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                OwnerMeResponse.from(ownerFacade.getMe(authentication.getName()))));
    }

    @PutMapping("/owners/me")
    public ResponseEntity<ApiResponse<Void>> updateMe(
            Authentication authentication,
            @Valid @RequestBody OwnerMeUpdateRequest request) {
        ownerFacade.updateMe(request.toCommand(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 사장님 본인 가게의 해결되지 않은 고민. 없으면 data 없이 성공으로 응답한다. */
    @GetMapping("/owners/me/concern")
    public ResponseEntity<ApiResponse<StoreConcernResponse>> getConcern(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(ownerFacade.getConcern(authentication.getName())
                .map(StoreConcernResponse::from)
                .orElse(null)));
    }

    /** 가게 고민을 올리거나, 해결되지 않은 고민이 있으면 통째로 고친다. */
    @PutMapping("/owners/me/concern")
    public ResponseEntity<ApiResponse<StoreConcernResponse>> saveConcern(
            Authentication authentication,
            @Valid @RequestBody StoreConcernSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(StoreConcernResponse.from(
                ownerFacade.saveConcern(request.toCommand(authentication.getName())))));
    }

    /** 「해결됐어요」. 해결되지 않은 고민을 내린다. 해결한 고민은 지우지 않고 기록으로 남는다. */
    @DeleteMapping("/owners/me/concern")
    public ResponseEntity<ApiResponse<Void>> resolveConcern(Authentication authentication) {
        ownerFacade.resolveConcern(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
