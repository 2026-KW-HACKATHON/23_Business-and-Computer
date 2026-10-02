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
import com.gakkum.backend.application.explore.dto.StoreExploreRequest;
import com.gakkum.backend.application.explore.dto.StoreExploreResponse;
import com.gakkum.backend.application.explore.dto.StoreExploreSort;
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

    /** 학생이 등록된 매장(사장님 프로필)을 업종으로 거르고 생성 시각순으로 정렬해 커서로 이어 조회하는 API */
    @GetMapping("/explore/stores")
    public ResponseEntity<ApiResponse<StoreExploreResponse>> exploreStores(
            Authentication authentication,
            @RequestParam(defaultValue = "LATEST") StoreExploreSort sort,
            @RequestParam(required = false) Long businessCategoryId,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String cursor) {
        StoreExploreRequest request = StoreExploreRequest.of(sort, businessCategoryId, size, cursor);
        StoreExploreResponse response = StoreExploreResponse.from(
                exploreFacade.exploreStores(request.toCommand(authentication.getName())));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
