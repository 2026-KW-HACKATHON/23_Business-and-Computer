package com.gakkum.backend.application.explore.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.explore.dto.ExploreRequest;
import com.gakkum.backend.application.explore.dto.ExploreResponse;
import com.gakkum.backend.application.explore.dto.ExploreSort;
import com.gakkum.backend.application.explore.dto.ExploreType;
import com.gakkum.backend.application.explore.facade.ExploreFacade;
import com.gakkum.backend.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ExploreController {

    private final ExploreFacade exploreFacade;

    /** 인증 사용자가 제안과 의뢰(취소 제외)를 대분류·종류로 거르고 정렬해 커서로 이어 조회하는 API */
    @GetMapping("/explore")
    public ResponseEntity<ApiResponse<ExploreResponse>> explore(
            Authentication authentication,
            @RequestParam(required = false) Long specialtyCategoryId,
            @RequestParam(defaultValue = "ALL") ExploreType type,
            @RequestParam(defaultValue = "LATEST") ExploreSort sort,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String cursor) {
        ExploreRequest request = ExploreRequest.of(specialtyCategoryId, type, sort, size, cursor);
        ExploreResponse response = ExploreResponse.from(
                exploreFacade.explore(request.toCommand(authentication.getName())));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
