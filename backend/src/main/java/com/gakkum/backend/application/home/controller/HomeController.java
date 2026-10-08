package com.gakkum.backend.application.home.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.home.dto.HomeResponse;
import com.gakkum.backend.application.home.facade.HomeFacade;
import com.gakkum.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class HomeController {

    private final HomeFacade homeFacade;

    /**
     * 로그인 사용자의 역할에 맞는 홈 본문을 한 번에 조회하는 API.
     * 섹션이 null이면 그 섹션의 조회에 실패한 것이고, 같은 요청을 다시 보내 재시도한다.
     */
    @GetMapping("/me/home")
    public ResponseEntity<ApiResponse<HomeResponse>> getHome(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(homeFacade.getHome(authentication.getName())));
    }
}
