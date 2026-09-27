package com.gakkum.backend.application.job.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.job.dto.ClosedJobListResponse;
import com.gakkum.backend.application.job.dto.JobCreateRequest;
import com.gakkum.backend.application.job.dto.JobDetailResponse;
import com.gakkum.backend.application.job.dto.JobSubmissionCreateRequest;
import com.gakkum.backend.application.job.dto.JobSubmissionCreateResponse;
import com.gakkum.backend.application.job.dto.JobSubmissionDetailResponse;
import com.gakkum.backend.application.job.dto.MatchedJobListResponse;
import com.gakkum.backend.application.job.dto.OpenJobListResponse;
import com.gakkum.backend.application.job.dto.PrepareSubmissionFileUploadRequest;
import com.gakkum.backend.application.job.dto.PrepareSubmissionFileUploadResponse;
import com.gakkum.backend.application.job.dto.StudentMatchedJobListResponse;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class JobController {

    private final JobFacade jobFacade;

    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<Void>> createJob(Authentication authentication, @Valid @RequestBody JobCreateRequest request) {
        jobFacade.createJob(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<JobDetailResponse>> getJobDetail(
            Authentication authentication, @PathVariable Long jobId) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobDetailResponse response = JobDetailResponse.from(jobFacade.getJobDetail(authentication.getName(), jobId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/jobs/{jobId}/submission")
    public ResponseEntity<ApiResponse<JobSubmissionDetailResponse>> getPendingSubmission(
            Authentication authentication, @PathVariable Long jobId) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobSubmissionDetailResponse response = JobSubmissionDetailResponse.from(
                jobFacade.getPendingSubmission(authentication.getName(), jobId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 매칭된 학생의 작업물 파일 업로드 준비 API(PresignedURL과 제출에 쓸 공개 파일 URL 반환) */
    @PostMapping("/jobs/{jobId}/submission/uploads")
    public ResponseEntity<ApiResponse<PrepareSubmissionFileUploadResponse>> prepareSubmissionFileUpload(
            Authentication authentication, @PathVariable Long jobId,
            @Valid @RequestBody PrepareSubmissionFileUploadRequest request) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        PrepareSubmissionFileUploadResponse response = PrepareSubmissionFileUploadResponse.from(
                jobFacade.prepareSubmissionFileUpload(request.toCommand(authentication.getName(), jobId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /** 매칭된 학생의 첫 초안 제출 API */
    @PostMapping("/jobs/{jobId}/submission")
    public ResponseEntity<ApiResponse<JobSubmissionCreateResponse>> submitDraft(
            Authentication authentication, @PathVariable Long jobId,
            @Valid @RequestBody JobSubmissionCreateRequest request) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobSubmissionCreateResponse response = JobSubmissionCreateResponse.from(
                jobFacade.submitDraft(request.toCommand(authentication.getName(), jobId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /** 수정 요청을 받은 매칭 학생의 수정안 제출 API */
    @PostMapping("/jobs/{jobId}/submission/revisions")
    public ResponseEntity<ApiResponse<JobSubmissionCreateResponse>> submitRevision(
            Authentication authentication, @PathVariable Long jobId,
            @Valid @RequestBody JobSubmissionCreateRequest request) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobSubmissionCreateResponse response = JobSubmissionCreateResponse.from(
                jobFacade.submitRevision(request.toCommand(authentication.getName(), jobId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /** 사장님이 검토 대기 제출물에 수정을 요청하는 API */
    @PostMapping("/jobs/{jobId}/submissions/{submissionId}/revision-request")
    public ResponseEntity<ApiResponse<Void>> requestRevision(
            Authentication authentication, @PathVariable Long jobId, @PathVariable Long submissionId) {
        if (jobId <= 0 || submissionId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        jobFacade.requestRevision(authentication.getName(), jobId, submissionId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 사장님이 검토 대기 제출물을 최종 결과로 수락해 의뢰를 즉시 완료하는 API */
    @PostMapping("/jobs/{jobId}/submissions/{submissionId}/complete")
    public ResponseEntity<ApiResponse<Void>> completeSubmission(
            Authentication authentication, @PathVariable Long jobId, @PathVariable Long submissionId) {
        if (jobId <= 0 || submissionId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        jobFacade.completeSubmission(authentication.getName(), jobId, submissionId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/me/jobs")
    public ResponseEntity<ApiResponse<?>> getJobs(
            Authentication authentication, @RequestParam(required = false) String status) {
        if ("OPEN".equals(status)) {
            OpenJobListResponse response = OpenJobListResponse.from(jobFacade.getOpenJobs(authentication.getName()));
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        if ("MATCHED".equals(status) && jobFacade.isStudent(authentication.getName())) {
            StudentMatchedJobListResponse response = StudentMatchedJobListResponse.from(
                    jobFacade.getStudentMatchedJobs(authentication.getName()));
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        if ("MATCHED".equals(status)) {
            MatchedJobListResponse response = MatchedJobListResponse.from(jobFacade.getMatchedJobs(authentication.getName()));
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        if ("CLOSED".equals(status)) {
            ClosedJobListResponse response = ClosedJobListResponse.from(jobFacade.getClosedJobs(authentication.getName()));
            return ResponseEntity.ok(ApiResponse.success(response));
        }

        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
}
