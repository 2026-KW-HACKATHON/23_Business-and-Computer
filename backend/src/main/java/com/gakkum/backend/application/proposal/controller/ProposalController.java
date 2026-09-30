package com.gakkum.backend.application.proposal.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.proposal.dto.ProposalCreateRequest;
import com.gakkum.backend.application.proposal.dto.ProposalCreateResponse;
import com.gakkum.backend.application.proposal.facade.ProposalFacade;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ProposalController {

    private final ProposalFacade proposalFacade;

    /** 학생이 특정 사장님에게 기존 의뢰와 무관한 제안을 보내는 API */
    @PostMapping("/proposals")
    public ResponseEntity<ApiResponse<ProposalCreateResponse>> createProposal(
            Authentication authentication, @Valid @RequestBody ProposalCreateRequest request) {
        ProposalCreateResponse response = ProposalCreateResponse.from(
                proposalFacade.createProposal(request.toCommand(authentication.getName())));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
