package com.gakkum.backend.application.proposal.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.proposal.dto.MyProposalListResponse;
import com.gakkum.backend.application.proposal.dto.ProposalCreateRequest;
import com.gakkum.backend.application.proposal.dto.ProposalCreateResponse;
import com.gakkum.backend.application.proposal.dto.ProposalDetailResponse;
import com.gakkum.backend.application.proposal.dto.ProposalJobStartRequest;
import com.gakkum.backend.application.proposal.dto.ProposalJobStartResponse;
import com.gakkum.backend.application.proposal.dto.ReceivedProposalListResponse;
import com.gakkum.backend.application.proposal.facade.ProposalFacade;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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

    /** 인증 사용자가 제안의 내용과 제안한 학생 정보를 조회하는 API */
    @GetMapping("/proposals/{proposalId}")
    public ResponseEntity<ApiResponse<ProposalDetailResponse>> getProposalDetail(
            Authentication authentication, @PathVariable Long proposalId) {
        ProposalDetailResponse response = ProposalDetailResponse.from(
                proposalFacade.getProposalDetail(authentication.getName(), proposalId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 제안한 학생이 확정 작업 조건에 동의하고 결제된 제안 의뢰의 작업을 시작하는 API */
    @PostMapping("/jobs/{jobId}/start")
    public ResponseEntity<ApiResponse<ProposalJobStartResponse>> startProposalJob(
            Authentication authentication,
            @PathVariable @Positive Long jobId,
            @Valid @RequestBody ProposalJobStartRequest request) {
        ProposalJobStartResponse response = ProposalJobStartResponse.from(
                proposalFacade.startProposalJob(request.toCommand(authentication.getName(), jobId)));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 로그인한 학생이 보낸 모든 제안을 최신순으로 조회하는 API */
    @GetMapping("/me/proposals")
    public ResponseEntity<ApiResponse<MyProposalListResponse>> getMyProposals(Authentication authentication) {
        MyProposalListResponse response = MyProposalListResponse.from(
                proposalFacade.getMyProposals(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 로그인한 사장님이 받은 모든 제안을 최신순으로 조회하는 API */
    @GetMapping("/me/received-proposals")
    public ResponseEntity<ApiResponse<ReceivedProposalListResponse>> getReceivedProposals(
            Authentication authentication) {
        ReceivedProposalListResponse response = ReceivedProposalListResponse.from(
                proposalFacade.getReceivedProposals(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
