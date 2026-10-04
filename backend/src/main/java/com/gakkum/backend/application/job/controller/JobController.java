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

import com.gakkum.backend.application.job.dto.JobApplicantProfileResponse;
import com.gakkum.backend.application.job.dto.JobApplicationCreateRequest;
import com.gakkum.backend.application.job.dto.JobApplicationCreateResponse;
import com.gakkum.backend.application.job.dto.JobApplicationListResponse;
import com.gakkum.backend.application.job.dto.JobCancelRequest;
import com.gakkum.backend.application.job.dto.JobCancelResponse;
import com.gakkum.backend.application.job.dto.JobCreateRequest;
import com.gakkum.backend.application.job.dto.JobDetailResponse;
import com.gakkum.backend.application.job.dto.JobListResponse;
import com.gakkum.backend.application.job.dto.JobSubmissionCreateRequest;
import com.gakkum.backend.application.job.dto.JobSubmissionResponse;
import com.gakkum.backend.application.job.dto.PrepareSubmissionFileUploadRequest;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.job.dto.JobApplicationSort;
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
    public ResponseEntity<ApiResponse<JobDetailResponse.Detail>> getJobDetail(
            Authentication authentication, @PathVariable Long jobId) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobDetailResponse.Detail response = JobDetailResponse.Detail.from(jobFacade.getJobDetail(authentication.getName(), jobId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 학생이 모집 중 의뢰에 지원서를 작성하는 API(마감 준수·페널티 확인 동의 필수) */
    @PostMapping("/jobs/{jobId}/applications")
    public ResponseEntity<ApiResponse<JobApplicationCreateResponse>> createJobApplication(
            Authentication authentication, @PathVariable Long jobId,
            @Valid @RequestBody JobApplicationCreateRequest request) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobApplicationCreateResponse response = JobApplicationCreateResponse.from(
                jobFacade.createJobApplication(request.toCommand(authentication.getName(), jobId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /** 사장님이 본인의 모집 중 의뢰에 지원한 대기 중 지원자 전체를 조회하는 API(sort 생략 시 최신 지원순) */
    @GetMapping("/jobs/{jobId}/applications")
    public ResponseEntity<ApiResponse<JobApplicationListResponse>> getJobApplications(
            Authentication authentication, @PathVariable Long jobId,
            @RequestParam(required = false) String sort) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // 빈 값·소문자·공백이 섞인 값을 허용하지 않도록 enum 변환에 맡기지 않고 직접 확인한다
        JobApplicationSort applicationSort = sort == null
                ? JobApplicationSort.LATEST
                : JobApplicationSort.find(sort).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
        JobApplicationListResponse response = JobApplicationListResponse.from(
                jobFacade.getJobApplications(authentication.getName(), jobId, applicationSort));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 사장님이 본인 의뢰의 지원자(모집 중) 또는 선정 학생(매칭·완료 후)의 학생 정보와 활동 이력을 조회하는 API */
    @GetMapping("/jobs/{jobId}/applications/{jobApplicationId}/profile")
    public ResponseEntity<ApiResponse<JobApplicantProfileResponse>> getJobApplicantProfile(
            Authentication authentication, @PathVariable Long jobId, @PathVariable Long jobApplicationId) {
        if (jobId <= 0 || jobApplicationId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobApplicantProfileResponse response = JobApplicantProfileResponse.from(
                jobFacade.getJobApplicantProfile(authentication.getName(), jobId, jobApplicationId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/jobs/{jobId}/submission")
    public ResponseEntity<ApiResponse<JobSubmissionResponse.Detail>> getPendingSubmission(
            Authentication authentication, @PathVariable Long jobId) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobSubmissionResponse.Detail response = JobSubmissionResponse.Detail.from(
                jobFacade.getPendingSubmission(authentication.getName(), jobId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 완료된 의뢰의 최종 결과물과 작업 이력 조회 API(의뢰한 사장님과 담당 학생만 조회 가능) */
    @GetMapping("/jobs/{jobId}/result")
    public ResponseEntity<ApiResponse<JobDetailResponse.Result>> getJobResult(
            Authentication authentication, @PathVariable Long jobId) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobDetailResponse.Result response = JobDetailResponse.Result.from(jobFacade.getJobResult(authentication.getName(), jobId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 매칭된 학생의 작업물 파일 업로드 준비 API(PresignedURL과 제출에 쓸 공개 파일 URL 반환) */
    @PostMapping("/jobs/{jobId}/submission/uploads")
    public ResponseEntity<ApiResponse<JobSubmissionResponse.PrepareFileUpload>> prepareSubmissionFileUpload(
            Authentication authentication, @PathVariable Long jobId,
            @Valid @RequestBody PrepareSubmissionFileUploadRequest request) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobSubmissionResponse.PrepareFileUpload response = JobSubmissionResponse.PrepareFileUpload.from(
                jobFacade.prepareSubmissionFileUpload(request.toCommand(authentication.getName(), jobId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /** 매칭된 학생의 첫 초안 제출 API */
    @PostMapping("/jobs/{jobId}/submission")
    public ResponseEntity<ApiResponse<JobSubmissionResponse.Create>> submitDraft(
            Authentication authentication, @PathVariable Long jobId,
            @Valid @RequestBody JobSubmissionCreateRequest request) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobSubmissionResponse.Create response = JobSubmissionResponse.Create.from(
                jobFacade.submitDraft(request.toCommand(authentication.getName(), jobId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /** 수정 요청을 받은 매칭 학생의 수정안 제출 API */
    @PostMapping("/jobs/{jobId}/submission/revisions")
    public ResponseEntity<ApiResponse<JobSubmissionResponse.Create>> submitRevision(
            Authentication authentication, @PathVariable Long jobId,
            @Valid @RequestBody JobSubmissionCreateRequest request) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobSubmissionResponse.Create response = JobSubmissionResponse.Create.from(
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

    /** 사장님이 모집 중 또는 진행 중인 본인 의뢰를 취소 이유·남길 말과 함께 취소하는 API(진행 중이면 학생 보상금 20%를 뺀 금액 환불 처리) */
    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<ApiResponse<JobCancelResponse>> cancelJob(
            Authentication authentication, @PathVariable Long jobId,
            @Valid @RequestBody JobCancelRequest request) {
        if (jobId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobCancelResponse response = JobCancelResponse.from(
                jobFacade.cancelJob(request.toCommand(authentication.getName(), jobId)));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me/jobs")
    public ResponseEntity<ApiResponse<?>> getJobs(
            Authentication authentication, @RequestParam(required = false) String status) {
        if ("OPEN".equals(status)) {
            JobListResponse.OpenJobList response = JobListResponse.OpenJobList.from(jobFacade.getOpenJobs(authentication.getName()));
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        if ("MATCHED".equals(status) && jobFacade.isStudent(authentication.getName())) {
            JobListResponse.StudentMatchedJobList response = JobListResponse.StudentMatchedJobList.from(
                    jobFacade.getStudentMatchedJobs(authentication.getName()));
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        if ("MATCHED".equals(status)) {
            JobListResponse.MatchedJobList response = JobListResponse.MatchedJobList.from(jobFacade.getMatchedJobs(authentication.getName()));
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        if ("CLOSED".equals(status)) {
            JobListResponse.ClosedJobList response = JobListResponse.ClosedJobList.from(jobFacade.getClosedJobs(authentication.getName()));
            return ResponseEntity.ok(ApiResponse.success(response));
        }

        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
}
