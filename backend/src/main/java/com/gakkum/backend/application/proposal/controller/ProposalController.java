package com.gakkum.backend.application.proposal.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.proposal.dto.ProposalCreateRequest;
import com.gakkum.backend.application.proposal.dto.ProposalCreateResponse;
import com.gakkum.backend.application.proposal.dto.ProposalDetailResponse;
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

    /** 사장님이 본인이 받은 제안의 내용과 제안한 학생 정보를 조회하는 API */
    @GetMapping("/proposals/{proposalId}")
    public ResponseEntity<ApiResponse<ProposalDetailResponse>> getReceivedProposal(
            Authentication authentication, @PathVariable Long proposalId) {
        ProposalDetailResponse response = ProposalDetailResponse.from(
                proposalFacade.getReceivedProposal(authentication.getName(), proposalId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
